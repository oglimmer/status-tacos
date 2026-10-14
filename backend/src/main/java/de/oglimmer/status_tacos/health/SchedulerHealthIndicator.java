/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.health;

import de.oglimmer.status_tacos.service.MonitorExecutionService;
import de.oglimmer.status_tacos.service.ScheduledMonitorService;
import de.oglimmer.status_tacos.service.SchedulerHeartbeat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * DOWN when the check job did not fire on this instance for two check intervals. The job fires on
 * every replica, so a replica without the lock is UP too. The last run shows only on the replica
 * that ran it.
 */
@Component
@Slf4j
public class SchedulerHealthIndicator implements HealthIndicator {

  static final int MAX_MISSED_INTERVALS = 2;

  private final MonitorExecutionService monitorExecutionService;
  private final SchedulerHeartbeat heartbeat;
  private final Clock clock;
  private final boolean schedulingEnabled;
  private final Duration maxSilence;

  public SchedulerHealthIndicator(
      MonitorExecutionService monitorExecutionService,
      SchedulerHeartbeat heartbeat,
      Clock clock,
      @Value("${monitor.scheduling.enabled:true}") boolean schedulingEnabled,
      @Value("${monitor.scheduling.check-interval:60000}") long checkIntervalMs) {
    this.monitorExecutionService = monitorExecutionService;
    this.heartbeat = heartbeat;
    this.clock = clock;
    this.schedulingEnabled = schedulingEnabled;
    this.maxSilence = Duration.ofMillis(checkIntervalMs * MAX_MISSED_INTERVALS);
  }

  @Override
  public Health health() {
    if (!schedulingEnabled) {
      return Health.up().withDetail("status", "Scheduling is disabled on this instance").build();
    }
    try {
      Instant lastFiredAt = heartbeat.getLastFiredAt();
      Instant lastSignOfLife =
          lastFiredAt != null
              ? lastFiredAt
              : heartbeat.getStartedAt().plusMillis(ScheduledMonitorService.INITIAL_DELAY_MS);
      Duration silence = Duration.between(lastSignOfLife, clock.instant());

      Health.Builder health;
      if (silence.compareTo(maxSilence) > 0) {
        health =
            Health.down()
                .withDetail(
                    "status", "The check job did not fire for " + silence.toSeconds() + "s");
      } else {
        health = Health.up().withDetail("status", "Scheduler is operational");
      }
      health.withDetail("activeMonitors", monitorExecutionService.getActiveMonitorCount());
      if (lastFiredAt != null) {
        health.withDetail("lastFiredAt", lastFiredAt);
      }
      SchedulerHeartbeat.Run lastRun = heartbeat.getLastRun();
      if (lastRun != null) {
        health
            .withDetail("lastRunFinishedAt", lastRun.finishedAt())
            .withDetail("lastRunMillis", lastRun.took().toMillis());
      }
      return health.build();

    } catch (Exception e) {
      log.error("Scheduler health check failed: {}", e.getMessage(), e);

      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("status", "Scheduler health check failed")
          .build();
    }
  }
}
