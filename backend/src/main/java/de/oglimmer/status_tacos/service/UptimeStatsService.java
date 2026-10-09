/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.dto.ResponseTimeDataPointDto;
import de.oglimmer.status_tacos.dto.ResponseTimeHistoryResponseDto;
import de.oglimmer.status_tacos.dto.StatsPeriod;
import de.oglimmer.status_tacos.dto.StatusDownPeriodsDto;
import de.oglimmer.status_tacos.dto.UptimeStatsResponseDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.repository.CheckPoint;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import de.oglimmer.status_tacos.repository.CheckRollupRepository;
import de.oglimmer.status_tacos.repository.CheckRollupRepository.HistogramRow;
import de.oglimmer.status_tacos.repository.CheckRollupRepository.HourRow;
import de.oglimmer.status_tacos.repository.CheckRollupRepository.OutageRow;
import de.oglimmer.status_tacos.repository.CheckRollupRepository.Transition;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.service.OutageTracker.Outage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes uptime stats on request.
 *
 * <p>A window is read from the roll-ups up to the watermark of the roll-up job, plus the raw check
 * results after the watermark. All reads run in one read-only transaction, so they see one
 * consistent snapshot: no check is missing or counted twice, and the numbers have no lag.
 *
 * <p>Uptime = successful checks / all checks, rounded down to 2 decimals. Response-time values
 * count successful checks only.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UptimeStatsService {

  static final int HISTORY_24H_INTERVAL_MINUTES = 3;
  static final int HISTORY_24H_DATA_POINTS = 480;

  private final CheckRollupRepository rollupRepository;
  private final CheckResultRepository checkResultRepository;
  private final MonitorRepository monitorRepository;
  private final Clock clock;

  /**
   * @return empty if the monitor is not in one of the tenants
   */
  public Optional<UptimeStatsResponseDto> getStats(
      Set<Integer> tenantIds, Integer monitorId, StatsPeriod period) {
    return monitorRepository
        .findByIdAndTenantIdIn(monitorId, tenantIds)
        .map(monitor -> computeStats(List.of(monitor), period).getFirst());
  }

  /** Stats of all monitors of the tenants. */
  public List<UptimeStatsResponseDto> getStatsOfAllMonitors(
      Set<Integer> tenantIds, StatsPeriod period) {
    List<Monitor> monitors = monitorRepository.findByTenantIdIn(tenantIds);
    return monitors.isEmpty() ? List.of() : computeStats(monitors, period);
  }

  /**
   * The last 24 hours of one monitor, from raw check results, in 3-minute buckets.
   *
   * @return empty if the monitor is not in one of the tenants
   */
  public Optional<ResponseTimeHistoryResponseDto> getResponseTimeHistory24h(
      Set<Integer> tenantIds, Integer monitorId) {
    return monitorRepository
        .findByIdAndTenantIdIn(monitorId, tenantIds)
        .map(monitor -> computeHistory24h(List.of(monitor)).getFirst());
  }

  /** The last 24 hours of all monitors of the tenants, in one read. */
  public List<ResponseTimeHistoryResponseDto> getResponseTimeHistory24hOfAllMonitors(
      Set<Integer> tenantIds) {
    List<Monitor> monitors = monitorRepository.findByTenantIdIn(tenantIds);
    return monitors.isEmpty() ? List.of() : computeHistory24h(monitors);
  }

  private List<UptimeStatsResponseDto> computeStats(List<Monitor> monitors, StatsPeriod period) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime start = period.windowStart(now);
    List<Integer> ids = monitors.stream().map(Monitor::getId).toList();
    Watermarks marks = readWatermarks(start, now);

    // Counts and chart: hourly roll-ups, then raw check results after the watermark.
    List<HourRow> hours = new ArrayList<>(rollupRepository.findHourly(ids, start, marks.hourly()));
    hours.addAll(rollupRepository.aggregateRawByHour(ids, marks.hourly(), now));
    Map<Integer, List<HourRow>> hoursByMonitor = groupBy(hours, HourRow::monitorId);

    Map<Integer, ResponseTimeHistogram> histograms = readHistograms(ids, period, start, now, marks);
    Map<Integer, List<StatusDownPeriodsDto>> downPeriods = readDownPeriods(ids, start, now, marks);

    return monitors.stream()
        .map(
            monitor -> {
              Totals totals = new Totals();
              List<HourRow> monitorHours = hoursByMonitor.getOrDefault(monitor.getId(), List.of());
              monitorHours.forEach(totals::add);
              ResponseTimeHistogram histogram =
                  histograms.getOrDefault(monitor.getId(), new ResponseTimeHistogram());
              return UptimeStatsResponseDto.builder()
                  .monitorId(monitor.getId())
                  .monitorName(monitor.getName())
                  .periodType(period)
                  .periodStart(start)
                  .periodEnd(now)
                  .intervalMinutes(period.getChartIntervalMinutes())
                  .totalChecks(totals.total)
                  .successfulChecks(totals.up)
                  .uptimePercentage(uptimePercentage(totals.up, totals.total))
                  .minResponseTimeMs(totals.rtMin)
                  .maxResponseTimeMs(totals.rtMax)
                  .avgResponseTimeMs(totals.avg())
                  .p99ResponseTimeMs(histogram.percentile(99, totals.rtMin, totals.rtMax))
                  .responseTimeDataPoints(
                      chart(monitorHours, start, period.getChartIntervalMinutes()))
                  .statusDownPeriods(downPeriods.getOrDefault(monitor.getId(), List.of()))
                  .build();
            })
        .toList();
  }

  private List<ResponseTimeHistoryResponseDto> computeHistory24h(List<Monitor> monitors) {
    LocalDateTime now = LocalDateTime.now(clock);
    // 480 aligned 3-minute buckets, the last one holds now.
    LocalDateTime start =
        floorToMinutes(now, HISTORY_24H_INTERVAL_MINUTES)
            .minusMinutes((long) (HISTORY_24H_DATA_POINTS - 1) * HISTORY_24H_INTERVAL_MINUTES);
    List<Integer> ids = monitors.stream().map(Monitor::getId).toList();
    Map<Integer, List<CheckPoint>> checksByMonitor =
        groupBy(checkResultRepository.findCheckPoints(ids, start, now), CheckPoint::monitorId);
    Watermarks marks = readWatermarks(start, now);
    Map<Integer, List<StatusDownPeriodsDto>> downPeriods = readDownPeriods(ids, start, now, marks);

    return monitors.stream()
        .map(
            monitor -> {
              List<CheckPoint> checks = checksByMonitor.getOrDefault(monitor.getId(), List.of());
              long up = checks.stream().filter(CheckPoint::isUp).count();
              TreeMap<LocalDateTime, Integer> maxByBucket = new TreeMap<>();
              for (CheckPoint check : checks) {
                if (check.isUp() && check.responseTimeMs() != null) {
                  maxByBucket.merge(
                      bucketStart(start, check.checkedAt(), HISTORY_24H_INTERVAL_MINUTES),
                      check.responseTimeMs(),
                      Math::max);
                }
              }
              return ResponseTimeHistoryResponseDto.builder()
                  .monitorId(monitor.getId())
                  .monitorName(monitor.getName())
                  .intervalMinutes(HISTORY_24H_INTERVAL_MINUTES)
                  .totalDataPoints(HISTORY_24H_DATA_POINTS)
                  .uptimePercentage24h(uptimePercentage(up, checks.size()))
                  .totalChecks24h(checks.size())
                  .successfulChecks24h((int) up)
                  .dataPoints(toDataPoints(maxByBucket))
                  .statusDownPeriods(downPeriods.getOrDefault(monitor.getId(), List.of()))
                  .build();
            })
        .toList();
  }

  /**
   * @param hourly end of the hourly roll-ups in the window, in [start, now]
   * @param daily end of the daily roll-ups in the window, in [start, hourly]
   * @param outagesUntil the outage table is complete until here; null if nothing is rolled up
   */
  private record Watermarks(
      LocalDateTime hourly, LocalDateTime daily, LocalDateTime outagesUntil) {}

  private Watermarks readWatermarks(LocalDateTime start, LocalDateTime now) {
    LocalDateTime hourlyDone = rollupRepository.findWatermark(CheckRollupRepository.HOURLY);
    LocalDateTime dailyDone = rollupRepository.findWatermark(CheckRollupRepository.DAILY);
    LocalDateTime hourly = clamp(hourlyDone, start, now);
    return new Watermarks(hourly, clamp(dailyDone, start, hourly), hourlyDone);
  }

  private Map<Integer, ResponseTimeHistogram> readHistograms(
      Collection<Integer> ids,
      StatsPeriod period,
      LocalDateTime start,
      LocalDateTime now,
      Watermarks marks) {
    List<HistogramRow> rows = new ArrayList<>();
    // Daily histograms only fit a window that starts at midnight.
    LocalDateTime hourlyFrom = start;
    if (period.getAlignment() == ChronoUnit.DAYS) {
      rows.addAll(rollupRepository.sumDailyHistogram(ids, start, marks.daily()));
      hourlyFrom = marks.daily();
    }
    rows.addAll(rollupRepository.sumHourlyHistogram(ids, hourlyFrom, marks.hourly()));
    rows.addAll(rollupRepository.rawHistogram(ids, marks.hourly(), now));

    Map<Integer, ResponseTimeHistogram> histograms = new HashMap<>();
    for (HistogramRow row : rows) {
      histograms
          .computeIfAbsent(row.monitorId(), id -> new ResponseTimeHistogram())
          .add(row.bucket(), row.count());
    }
    return histograms;
  }

  /**
   * Outages from the outage table, updated with the raw checks after the watermark, clipped to the
   * window.
   *
   * <p>Raw checks are never read before the window start. When the roll-up job is behind the window
   * start (backfill, or the job was stopped), the outage table does not know the state at the
   * start. Then the last check before the start decides if an outage was open at the start.
   */
  private Map<Integer, List<StatusDownPeriodsDto>> readDownPeriods(
      Collection<Integer> ids, LocalDateTime start, LocalDateTime now, Watermarks marks) {
    LocalDateTime outagesUntil = marks.outagesUntil();
    boolean tableCoversStart = outagesUntil != null && !outagesUntil.isBefore(start);
    LocalDateTime tailFrom = tableCoversStart ? outagesUntil : start;

    Map<Integer, List<OutageRow>> stored =
        tableCoversStart
            ? groupBy(rollupRepository.findOutages(ids, start, now), OutageRow::monitorId)
            : Map.of();
    Set<Integer> downAtStart =
        tableCoversStart ? Set.of() : rollupRepository.findDownBefore(ids, start);
    Map<Integer, List<Transition>> tail =
        tailFrom.isBefore(now)
            ? groupBy(rollupRepository.findTransitions(ids, tailFrom, now), Transition::monitorId)
            : Map.of();

    Map<Integer, List<StatusDownPeriodsDto>> result = new HashMap<>();
    for (Integer id : ids) {
      List<Outage> outages = new ArrayList<>();
      // Started before the window, so clip() moves its start to the window start.
      Outage open = downAtStart.contains(id) ? new Outage(null, start, null) : null;
      for (OutageRow row : stored.getOrDefault(id, List.of())) {
        if (row.end() == null) {
          open = new Outage(row.id(), row.start(), null);
        } else {
          outages.add(new Outage(row.id(), row.start(), row.end()));
        }
      }
      OutageTracker tracker = new OutageTracker(open);
      tail.getOrDefault(id, List.of()).forEach(t -> tracker.accept(t.checkedAt(), t.up()));
      outages.addAll(tracker.result());

      result.put(
          id,
          outages.stream()
              .map(o -> clip(o, start, now))
              .flatMap(Optional::stream)
              .sorted(Comparator.comparing(StatusDownPeriodsDto::getStart))
              .toList());
    }
    return result;
  }

  private static Optional<StatusDownPeriodsDto> clip(
      Outage outage, LocalDateTime start, LocalDateTime now) {
    LocalDateTime from = outage.start().isAfter(start) ? outage.start() : start;
    LocalDateTime to = outage.isOpen() || outage.end().isAfter(now) ? now : outage.end();
    return from.isBefore(to)
        ? Optional.of(StatusDownPeriodsDto.builder().start(from).end(to).build())
        : Optional.empty();
  }

  /** Max response time per chart bucket. Buckets start at the window start. */
  private static List<ResponseTimeDataPointDto> chart(
      List<HourRow> hours, LocalDateTime start, int intervalMinutes) {
    TreeMap<LocalDateTime, Integer> maxByBucket = new TreeMap<>();
    for (HourRow hour : hours) {
      if (hour.rtMax() != null) {
        maxByBucket.merge(
            bucketStart(start, hour.bucketStart(), intervalMinutes), hour.rtMax(), Math::max);
      }
    }
    return toDataPoints(maxByBucket);
  }

  private static List<ResponseTimeDataPointDto> toDataPoints(Map<LocalDateTime, Integer> values) {
    return values.entrySet().stream()
        .map(
            e ->
                ResponseTimeDataPointDto.builder()
                    .timestamp(e.getKey())
                    .maxResponseTimeMs(e.getValue())
                    .build())
        .toList();
  }

  static BigDecimal uptimePercentage(long up, long total) {
    if (total == 0) {
      return null;
    }
    // Rounded down: 99.996 % is shown as 99.99 %, never as 100 %.
    return BigDecimal.valueOf(up * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN);
  }

  private static LocalDateTime bucketStart(
      LocalDateTime start, LocalDateTime time, int intervalMinutes) {
    long index = Duration.between(start, time).toMinutes() / intervalMinutes;
    return start.plusMinutes(index * intervalMinutes);
  }

  private static LocalDateTime floorToMinutes(LocalDateTime time, int minutes) {
    LocalDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
    return hour.plusMinutes(time.getMinute() / minutes * minutes);
  }

  private static LocalDateTime clamp(LocalDateTime value, LocalDateTime min, LocalDateTime max) {
    if (value == null || value.isBefore(min)) {
      return min;
    }
    return value.isAfter(max) ? max : value;
  }

  private static <T> Map<Integer, List<T>> groupBy(List<T> rows, Function<T, Integer> key) {
    return rows.stream().collect(Collectors.groupingBy(key));
  }

  private static final class Totals {
    long total;
    long up;
    long rtCount;
    long rtSum;
    Integer rtMin;
    Integer rtMax;

    void add(HourRow row) {
      total += row.totalChecks();
      up += row.upChecks();
      rtCount += row.rtCount();
      rtSum += row.rtSum();
      if (row.rtMin() != null && (rtMin == null || row.rtMin() < rtMin)) {
        rtMin = row.rtMin();
      }
      if (row.rtMax() != null && (rtMax == null || row.rtMax() > rtMax)) {
        rtMax = row.rtMax();
      }
    }

    Integer avg() {
      return rtCount == 0 ? null : (int) Math.round((double) rtSum / rtCount);
    }
  }
}
