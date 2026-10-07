/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL for the check roll-ups (MariaDB). All time ranges are half-open: {@code [from, to)}.
 *
 * <p>Response-time values (rt_*) and histograms count successful checks only.
 */
@Repository
@RequiredArgsConstructor
public class CheckRollupRepository {

  public static final String HOURLY = "hourly";
  public static final String DAILY = "daily";

  /** Rows of one hour, from {@code check_rollup_hourly} or computed from raw check results. */
  public record HourRow(
      int monitorId,
      LocalDateTime bucketStart,
      long totalChecks,
      long upChecks,
      long rtCount,
      long rtSum,
      Integer rtMin,
      Integer rtMax) {}

  public record HistogramRow(int monitorId, int bucket, long count) {}

  public record OutageRow(long id, int monitorId, LocalDateTime start, LocalDateTime end) {}

  public record Transition(int monitorId, LocalDateTime checkedAt, boolean up) {}

  /** Tables the cleanup deletes from, with the time column that decides the age. */
  public enum Retained {
    CHECK_RESULTS("check_results", "checked_at"),
    HOURLY("check_rollup_hourly", "bucket_start"),
    HOURLY_HISTOGRAM("check_rollup_hourly_histogram", "bucket_start"),
    DAILY_HISTOGRAM("check_rollup_daily_histogram", "bucket_start"),
    OUTAGES("monitor_outage", "ended_at");

    private final String sql;

    Retained(String table, String column) {
      // Only constants go into this SQL.
      this.sql = "DELETE FROM " + table + " WHERE " + column + " < :cutoff LIMIT :limit";
    }
  }

  private static final String RAW_AGGREGATES =
      """
      COUNT(*) AS total_checks,
      COALESCE(SUM(is_up), 0) AS up_checks,
      COALESCE(SUM(CASE WHEN is_up = 1 AND response_time_ms IS NOT NULL THEN 1 ELSE 0 END), 0)
        AS rt_count,
      COALESCE(SUM(CASE WHEN is_up = 1 THEN response_time_ms END), 0) AS rt_sum,
      MIN(CASE WHEN is_up = 1 THEN response_time_ms END) AS rt_min,
      MAX(CASE WHEN is_up = 1 THEN response_time_ms END) AS rt_max
      """;

  /** Same formula as {@code ResponseTimeHistogram.bucketOf}. */
  private static final String RT_BUCKET = "FLOOR(LN(GREATEST(response_time_ms, 1)) / LN(1.05))";

  private static final String HOUR_OF_CHECK =
      "CAST(DATE_FORMAT(checked_at, '%Y-%m-%d %H:00:00') AS DATETIME)";

  private final NamedParameterJdbcTemplate jdbc;

  // --- watermarks ---

  /** Reads a watermark and locks its row until the transaction ends. */
  public LocalDateTime lockWatermark(String name) {
    return jdbc.queryForObject(
        "SELECT done_until FROM rollup_state WHERE name = :name FOR UPDATE",
        Map.of("name", name),
        LocalDateTime.class);
  }

  public LocalDateTime findWatermark(String name) {
    return jdbc.queryForObject(
        "SELECT done_until FROM rollup_state WHERE name = :name",
        Map.of("name", name),
        LocalDateTime.class);
  }

  public void updateWatermark(String name, LocalDateTime doneUntil) {
    jdbc.update(
        "UPDATE rollup_state SET done_until = :doneUntil WHERE name = :name",
        Map.of("name", name, "doneUntil", doneUntil));
  }

  public LocalDateTime findOldestCheckTime() {
    return jdbc.queryForObject(
        "SELECT MIN(checked_at) FROM check_results", Map.of(), LocalDateTime.class);
  }

  public LocalDateTime findOldestHourlyHistogramBucket() {
    return jdbc.queryForObject(
        "SELECT MIN(bucket_start) FROM check_rollup_hourly_histogram",
        Map.of(),
        LocalDateTime.class);
  }

  // --- roll-up job ---

  /** Recomputes the hourly row and histogram of all monitors for one hour. Idempotent. */
  public void rollUpHour(LocalDateTime hour) {
    var params = range(hour, hour.plusHours(1)).addValue("hour", hour);
    jdbc.update("DELETE FROM check_rollup_hourly WHERE bucket_start = :hour", params);
    jdbc.update(
        "INSERT INTO check_rollup_hourly (monitor_id, bucket_start, total_checks, up_checks,"
            + " rt_count, rt_sum, rt_min, rt_max) SELECT monitor_id, :hour, "
            + RAW_AGGREGATES
            + " FROM check_results WHERE checked_at >= :from AND checked_at < :to"
            + " GROUP BY monitor_id",
        params);
    jdbc.update("DELETE FROM check_rollup_hourly_histogram WHERE bucket_start = :hour", params);
    jdbc.update(
        "INSERT INTO check_rollup_hourly_histogram (monitor_id, bucket_start, rt_bucket, cnt)"
            + " SELECT monitor_id, :hour, "
            + RT_BUCKET
            + " AS rt_bucket, COUNT(*) FROM check_results"
            + " WHERE checked_at >= :from AND checked_at < :to"
            + " AND is_up = 1 AND response_time_ms IS NOT NULL"
            + " GROUP BY monitor_id, rt_bucket",
        params);
  }

  /** Recomputes the daily histogram of all monitors from the hourly histogram. Idempotent. */
  public void rollUpDay(LocalDateTime day) {
    var params = range(day, day.plusDays(1)).addValue("day", day);
    jdbc.update("DELETE FROM check_rollup_daily_histogram WHERE bucket_start = :day", params);
    jdbc.update(
        "INSERT INTO check_rollup_daily_histogram (monitor_id, bucket_start, rt_bucket, cnt)"
            + " SELECT monitor_id, :day, rt_bucket, SUM(cnt) FROM check_rollup_hourly_histogram"
            + " WHERE bucket_start >= :from AND bucket_start < :to"
            + " GROUP BY monitor_id, rt_bucket",
        params);
  }

  /** Open outages of all monitors. */
  public List<OutageRow> findOpenOutages() {
    return jdbc.query(
        "SELECT id, monitor_id, started_at, ended_at FROM monitor_outage WHERE ended_at IS NULL",
        Map.of(),
        CheckRollupRepository::outage);
  }

  public void insertOutage(int monitorId, LocalDateTime start, LocalDateTime end) {
    var params =
        new MapSqlParameterSource()
            .addValue("monitorId", monitorId)
            .addValue("start", start)
            .addValue("end", end);
    jdbc.update(
        "INSERT INTO monitor_outage (monitor_id, started_at, ended_at)"
            + " VALUES (:monitorId, :start, :end)",
        params);
  }

  public void closeOutage(long id, LocalDateTime end) {
    jdbc.update(
        "UPDATE monitor_outage SET ended_at = :end WHERE id = :id", Map.of("id", id, "end", end));
  }

  /**
   * Status changes of all monitors in the range: the first check of each monitor, then every check
   * whose up/down state differs from the check before it.
   */
  public List<Transition> findTransitions(LocalDateTime from, LocalDateTime to) {
    return jdbc.query(transitionsSql(""), range(from, to), CheckRollupRepository::transition);
  }

  /** Deletes up to {@code limit} rows older than {@code cutoff}. Returns the deleted rows. */
  public int deleteOlderThan(Retained table, LocalDateTime cutoff, int limit) {
    return jdbc.update(table.sql, Map.of("cutoff", cutoff, "limit", limit));
  }

  // --- read path ---

  public List<HourRow> findHourly(
      Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to) {
    return jdbc.query(
        "SELECT monitor_id, bucket_start, total_checks, up_checks, rt_count, rt_sum, rt_min,"
            + " rt_max FROM check_rollup_hourly WHERE monitor_id IN (:ids)"
            + " AND bucket_start >= :from AND bucket_start < :to",
        range(from, to).addValue("ids", monitorIds),
        CheckRollupRepository::hourRow);
  }

  /** The same rows as {@link #findHourly}, computed from raw check results. */
  public List<HourRow> aggregateRawByHour(
      Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to) {
    return jdbc.query(
        "SELECT monitor_id, "
            + HOUR_OF_CHECK
            + " AS bucket_start, "
            + RAW_AGGREGATES
            + " FROM check_results WHERE monitor_id IN (:ids)"
            + " AND checked_at >= :from AND checked_at < :to"
            + " GROUP BY monitor_id, bucket_start",
        range(from, to).addValue("ids", monitorIds),
        CheckRollupRepository::hourRow);
  }

  public List<HistogramRow> sumHourlyHistogram(
      Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to) {
    return sumHistogram("check_rollup_hourly_histogram", monitorIds, from, to);
  }

  public List<HistogramRow> sumDailyHistogram(
      Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to) {
    return sumHistogram("check_rollup_daily_histogram", monitorIds, from, to);
  }

  public List<HistogramRow> rawHistogram(
      Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to) {
    return jdbc.query(
        "SELECT monitor_id, "
            + RT_BUCKET
            + " AS rt_bucket, COUNT(*) AS cnt FROM check_results WHERE monitor_id IN (:ids)"
            + " AND checked_at >= :from AND checked_at < :to"
            + " AND is_up = 1 AND response_time_ms IS NOT NULL"
            + " GROUP BY monitor_id, rt_bucket",
        range(from, to).addValue("ids", monitorIds),
        CheckRollupRepository::histogramRow);
  }

  /** Outages that overlap the range, open ones included. */
  public List<OutageRow> findOutages(
      Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to) {
    return jdbc.query(
        "SELECT id, monitor_id, started_at, ended_at FROM monitor_outage"
            + " WHERE monitor_id IN (:ids) AND started_at < :to"
            + " AND (ended_at IS NULL OR ended_at > :from)"
            + " ORDER BY monitor_id, started_at",
        range(from, to).addValue("ids", monitorIds),
        CheckRollupRepository::outage);
  }

  public List<Transition> findTransitions(
      Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to) {
    return jdbc.query(
        transitionsSql(" AND monitor_id IN (:ids)"),
        range(from, to).addValue("ids", monitorIds),
        CheckRollupRepository::transition);
  }

  /**
   * Monitors whose last check before {@code before} was DOWN. One index lookup per monitor
   * (idx_check_monitor_time), so it does not scan older check results.
   */
  public Set<Integer> findDownBefore(Collection<Integer> monitorIds, LocalDateTime before) {
    Set<Integer> down = new HashSet<>();
    for (Integer monitorId : monitorIds) {
      List<Boolean> last =
          jdbc.query(
              "SELECT is_up FROM check_results WHERE monitor_id = :monitorId"
                  + " AND checked_at < :before ORDER BY checked_at DESC, id DESC LIMIT 1",
              Map.of("monitorId", monitorId, "before", before),
              (rs, rowNum) -> rs.getBoolean("is_up"));
      if (!last.isEmpty() && !last.getFirst()) {
        down.add(monitorId);
      }
    }
    return down;
  }

  // --- helpers ---

  private List<HistogramRow> sumHistogram(
      String table, Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to) {
    return jdbc.query(
        "SELECT monitor_id, rt_bucket, SUM(cnt) AS cnt FROM "
            + table
            + " WHERE monitor_id IN (:ids) AND bucket_start >= :from AND bucket_start < :to"
            + " GROUP BY monitor_id, rt_bucket",
        range(from, to).addValue("ids", monitorIds),
        CheckRollupRepository::histogramRow);
  }

  private static String transitionsSql(String monitorFilter) {
    return "SELECT monitor_id, checked_at, is_up FROM ("
        + " SELECT monitor_id, checked_at, is_up, id,"
        + " LAG(is_up) OVER (PARTITION BY monitor_id ORDER BY checked_at, id) AS prev_up"
        + " FROM check_results WHERE checked_at >= :from AND checked_at < :to"
        + monitorFilter
        + ") t WHERE prev_up IS NULL OR prev_up <> is_up"
        + " ORDER BY monitor_id, checked_at, id";
  }

  private static MapSqlParameterSource range(LocalDateTime from, LocalDateTime to) {
    return new MapSqlParameterSource().addValue("from", from).addValue("to", to);
  }

  private static HourRow hourRow(ResultSet rs, int rowNum) throws SQLException {
    return new HourRow(
        rs.getInt("monitor_id"),
        rs.getObject("bucket_start", LocalDateTime.class),
        rs.getLong("total_checks"),
        rs.getLong("up_checks"),
        rs.getLong("rt_count"),
        rs.getLong("rt_sum"),
        rs.getObject("rt_min", Integer.class),
        rs.getObject("rt_max", Integer.class));
  }

  private static HistogramRow histogramRow(ResultSet rs, int rowNum) throws SQLException {
    return new HistogramRow(rs.getInt("monitor_id"), rs.getInt("rt_bucket"), rs.getLong("cnt"));
  }

  private static OutageRow outage(ResultSet rs, int rowNum) throws SQLException {
    return new OutageRow(
        rs.getLong("id"),
        rs.getInt("monitor_id"),
        rs.getObject("started_at", LocalDateTime.class),
        rs.getObject("ended_at", LocalDateTime.class));
  }

  private static Transition transition(ResultSet rs, int rowNum) throws SQLException {
    return new Transition(
        rs.getInt("monitor_id"),
        rs.getObject("checked_at", LocalDateTime.class),
        rs.getBoolean("is_up"));
  }
}
