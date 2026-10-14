/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    value = "monitor.scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ScheduledMonitorService {

  /** The check job first fires this long after start-up. */
  public static final long INITIAL_DELAY_MS = 5000;

  private final MonitorCheckJob monitorCheckJob;
  private final CheckRollupService checkRollupService;
  private final SchedulerHeartbeat heartbeat;

  /**
   * Fires on every replica. The heartbeat is the sign of life of this instance's scheduler (see
   * SchedulerHealthIndicator). The job itself runs on one replica only (see MonitorCheckJob).
   */
  @Scheduled(
      initialDelay = INITIAL_DELAY_MS,
      fixedRateString = "${monitor.scheduling.check-interval:60000}")
  public void executeAllMonitorChecks() {
    heartbeat.checksFired();
    monitorCheckJob.run();
  }

  @Scheduled(
      initialDelayString = "${monitor.scheduling.rollup-initial-delay:30000}",
      fixedDelayString = "${monitor.scheduling.rollup-interval:60000}")
  @SchedulerLock(name = "check-rollup", lockAtMostFor = "PT10M")
  public void rollUpCheckResults() {
    try {
      checkRollupService.rollUp();
    } catch (Exception e) {
      log.error("Error during check roll-up: {}", e.getMessage(), e);
    }
  }

  @Scheduled(cron = "${monitor.scheduling.cleanup-cron:0 0 2 * * *}")
  @SchedulerLock(name = "cleanup", lockAtMostFor = "PT1H")
  public void cleanupOldData() {
    log.info("Starting cleanup of old data");
    try {
      long startTime = System.currentTimeMillis();
      checkRollupService.cleanup();
      log.info("Completed data cleanup in {}ms", System.currentTimeMillis() - startTime);
    } catch (Exception e) {
      log.error("Error during data cleanup: {}", e.getMessage(), e);
    }
  }
}
