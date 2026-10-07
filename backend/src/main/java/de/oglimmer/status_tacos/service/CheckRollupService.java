/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.repository.CheckRollupRepository;
import de.oglimmer.status_tacos.repository.CheckRollupRepository.Retained;
import de.oglimmer.status_tacos.repository.CheckRollupRepository.Transition;
import de.oglimmer.status_tacos.service.OutageTracker.Outage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rolls up check results into hourly rows, hourly and daily response-time histograms and outages.
 * Also deletes old data.
 *
 * <p>Each closed hour is rolled up once, in one transaction that also moves the watermark. The
 * watermark row is locked, so two runs at the same time cannot roll up the same hour. A roll-up of
 * an hour deletes and rewrites its rows, so a repeated hour gives the same result.
 *
 * <p>On the first start the watermark is empty. The job then rolls up all existing check results
 * (backfill), a part in each run.
 */
@Service
@Slf4j
public class CheckRollupService {

  /** Checks can commit a little after their checked_at. An hour is rolled up after this delay. */
  static final Duration GRACE = Duration.ofMinutes(2);

  /** Max run time of one {@link #rollUp()} call, so a backfill does not hold the lock for long. */
  static final Duration RUN_BUDGET = Duration.ofMinutes(4);

  /** The longest stats period is 90 days. Its window starts up to 1 day earlier (alignment). */
  static final int ROLLUP_RETENTION_DAYS = 92;

  /** The 7-day period reads hourly histograms, older days use the daily histogram. */
  static final int HOURLY_HISTOGRAM_RETENTION_DAYS = 8;

  static final int DELETE_BATCH_SIZE = 5000;

  private final CheckRollupRepository repository;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final int rawRetentionDays;
  private final AtomicReference<LocalDateTime> hourlyWatermark = new AtomicReference<>();

  public CheckRollupService(
      CheckRollupRepository repository,
      TransactionTemplate transactionTemplate,
      Clock clock,
      MeterRegistry meterRegistry,
      @Value("${monitor.cleanup.retention-days:90}") int rawRetentionDays) {
    this.repository = repository;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
    this.rawRetentionDays = rawRetentionDays;
    Gauge.builder("status_tacos.rollup.lag", this::lagSeconds)
        .description("Seconds between now and the end of the last rolled-up hour")
        .baseUnit("seconds")
        .register(meterRegistry);
  }

  @PostConstruct
  void validateRetention() {
    // The 24h view reads raw check results.
    if (rawRetentionDays < 2) {
      throw new IllegalStateException(
          "monitor.cleanup.retention-days must be at least 2, is " + rawRetentionDays);
    }
  }

  /** Rolls up all closed hours and days, within the run budget. Returns the rolled-up hours. */
  public int rollUp() {
    long startTime = System.currentTimeMillis();
    Instant deadline = clock.instant().plus(RUN_BUDGET);
    int hours = repeat(this::rollUpNextHour, deadline);
    int days = repeat(this::rollUpNextDay, deadline);
    hourlyWatermark.set(repository.findWatermark(CheckRollupRepository.HOURLY));
    if (hours > 0 || days > 0) {
      log.info(
          "Rolled up {} hours and {} days in {}ms, rolled up until {}",
          hours,
          days,
          System.currentTimeMillis() - startTime,
          hourlyWatermark.get());
    }
    return hours;
  }

  /** Deletes data older than its retention, in small batches. */
  public void cleanup() {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime hourlyDone = repository.findWatermark(CheckRollupRepository.HOURLY);
    LocalDateTime dailyDone = repository.findWatermark(CheckRollupRepository.DAILY);
    LocalDateTime rollupCutoff = now.truncatedTo(ChronoUnit.DAYS).minusDays(ROLLUP_RETENTION_DAYS);

    // Raw check results and hourly histograms are deleted only after they are rolled up.
    deleteOlderThan(Retained.CHECK_RESULTS, notAfter(now.minusDays(rawRetentionDays), hourlyDone));
    deleteOlderThan(
        Retained.HOURLY_HISTOGRAM,
        notAfter(
            now.truncatedTo(ChronoUnit.HOURS).minusDays(HOURLY_HISTOGRAM_RETENTION_DAYS),
            dailyDone));
    deleteOlderThan(Retained.HOURLY, rollupCutoff);
    deleteOlderThan(Retained.DAILY_HISTOGRAM, rollupCutoff);
    deleteOlderThan(Retained.OUTAGES, rollupCutoff);
  }

  private int repeat(Supplier<Boolean> step, Instant deadline) {
    int count = 0;
    while (clock.instant().isBefore(deadline)
        && Boolean.TRUE.equals(transactionTemplate.execute(status -> step.get()))) {
      count++;
    }
    return count;
  }

  private boolean rollUpNextHour() {
    LocalDateTime hour = repository.lockWatermark(CheckRollupRepository.HOURLY);
    if (hour == null) {
      LocalDateTime oldest = repository.findOldestCheckTime();
      if (oldest == null) {
        return false;
      }
      hour = oldest.truncatedTo(ChronoUnit.HOURS);
    }
    LocalDateTime end = hour.plusHours(1);
    if (end.plus(GRACE).isAfter(LocalDateTime.now(clock))) {
      return false;
    }
    repository.rollUpHour(hour);
    rollUpOutages(hour, end);
    repository.updateWatermark(CheckRollupRepository.HOURLY, end);
    return true;
  }

  private void rollUpOutages(LocalDateTime from, LocalDateTime to) {
    Map<Integer, Outage> openOutages =
        repository.findOpenOutages().stream()
            .collect(
                Collectors.toMap(
                    CheckRollupRepository.OutageRow::monitorId,
                    o -> new Outage(o.id(), o.start(), null)));
    Map<Integer, List<Transition>> transitionsByMonitor =
        repository.findTransitions(from, to).stream()
            .collect(
                Collectors.groupingBy(
                    Transition::monitorId, LinkedHashMap::new, Collectors.toList()));

    transitionsByMonitor.forEach(
        (monitorId, transitions) -> {
          OutageTracker tracker = new OutageTracker(openOutages.get(monitorId));
          transitions.forEach(t -> tracker.accept(t.checkedAt(), t.up()));
          for (Outage outage : tracker.result()) {
            if (outage.id() == null) {
              repository.insertOutage(monitorId, outage.start(), outage.end());
            } else if (!outage.isOpen()) {
              repository.closeOutage(outage.id(), outage.end());
            }
          }
        });
  }

  private boolean rollUpNextDay() {
    LocalDateTime day = repository.lockWatermark(CheckRollupRepository.DAILY);
    LocalDateTime hourlyDone = repository.findWatermark(CheckRollupRepository.HOURLY);
    if (hourlyDone == null) {
      return false;
    }
    if (day == null) {
      LocalDateTime oldest = repository.findOldestHourlyHistogramBucket();
      if (oldest == null) {
        return false;
      }
      day = oldest.truncatedTo(ChronoUnit.DAYS);
    }
    LocalDateTime end = day.plusDays(1);
    if (end.isAfter(hourlyDone)) {
      return false;
    }
    repository.rollUpDay(day);
    repository.updateWatermark(CheckRollupRepository.DAILY, end);
    return true;
  }

  private void deleteOlderThan(Retained table, LocalDateTime cutoff) {
    if (cutoff == null) {
      return;
    }
    long total = 0;
    int deleted;
    do {
      deleted =
          transactionTemplate.execute(
              status -> repository.deleteOlderThan(table, cutoff, DELETE_BATCH_SIZE));
      total += deleted;
    } while (deleted == DELETE_BATCH_SIZE);
    if (total > 0) {
      log.info("Deleted {} rows of {} older than {}", total, table, cutoff);
    }
  }

  private double lagSeconds() {
    LocalDateTime watermark = hourlyWatermark.get();
    return watermark == null
        ? Double.NaN
        : Duration.between(watermark, LocalDateTime.now(clock)).toSeconds();
  }

  /** The cutoff, but not after the watermark. Null (delete nothing) if nothing is rolled up. */
  private static LocalDateTime notAfter(LocalDateTime cutoff, LocalDateTime watermark) {
    if (watermark == null) {
      return null;
    }
    return cutoff.isBefore(watermark) ? cutoff : watermark;
  }
}
