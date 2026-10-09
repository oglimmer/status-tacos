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

  private final MonitorExecutionService monitorExecutionService;
  private final CheckRollupService checkRollupService;

  // One replica runs the checks. lockAtLeastFor keeps a second replica out of the same interval.
  @Scheduled(initialDelay = 5000, fixedRateString = "${monitor.scheduling.check-interval:60000}")
  @SchedulerLock(name = "monitor-checks", lockAtLeastFor = "PT10S", lockAtMostFor = "PT2M")
  public void executeAllMonitorChecks() {
    log.debug("Starting scheduled monitor checks");

    try {
      long startTime = System.currentTimeMillis();
      monitorExecutionService.executeAllActiveMonitors();
      long duration = System.currentTimeMillis() - startTime;

      log.debug("Completed scheduled monitor checks in {}ms", duration);

    } catch (Exception e) {
      log.error("Error during scheduled monitor checks: {}", e.getMessage(), e);
    }
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
