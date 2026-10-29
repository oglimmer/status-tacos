/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Raw check results for the Prometheus query API, as small rows. */
@Repository
@RequiredArgsConstructor
public class ProbeSampleRepository {

  /** One check. {@code timeMillis} is epoch milliseconds (checked_at is UTC). */
  public record ProbeSample(
      int monitorId, long timeMillis, boolean up, Integer responseTimeMs, Integer statusCode) {}

  private final NamedParameterJdbcTemplate jdbc;

  /**
   * Checks of the monitors in [from, to], ordered by monitor and time. At most {@code limit} rows
   * (idx_check_monitor_time).
   */
  public List<ProbeSample> findSamples(
      Collection<Integer> monitorIds, LocalDateTime from, LocalDateTime to, int limit) {
    var params =
        new MapSqlParameterSource()
            .addValue("ids", monitorIds)
            .addValue("from", from)
            .addValue("to", to)
            .addValue("limit", limit);
    return jdbc.query(
        "SELECT monitor_id, checked_at, is_up, response_time_ms, status_code FROM check_results"
            + " WHERE monitor_id IN (:ids) AND checked_at >= :from AND checked_at <= :to"
            + " ORDER BY monitor_id, checked_at, id LIMIT :limit",
        params,
        (rs, rowNum) ->
            new ProbeSample(
                rs.getInt("monitor_id"),
                rs.getObject("checked_at", LocalDateTime.class)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli(),
                rs.getBoolean("is_up"),
                rs.getObject("response_time_ms", Integer.class),
                rs.getObject("status_code", Integer.class)));
  }
}
