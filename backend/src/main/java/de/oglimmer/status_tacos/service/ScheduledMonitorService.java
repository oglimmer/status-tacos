/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
  private final CheckResultService checkResultService;
  private final UptimeStatsService uptimeStatsService;
  private final TenantService tenantService;

  @Value("${monitor.cleanup.retention-days:90}")
  private int retentionDays;

  @Value("${monitor.retry.consecutive-failures-threshold:3}")
  private int consecutiveFailuresThreshold;

  @Scheduled(initialDelay = 5000, fixedRateString = "${monitor.scheduling.check-interval:60000}")
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

  // we need to rethink this. I think it's not needed
  //    @Scheduled(fixedRateString = "${monitor.scheduling.retry-interval:300000}")
  //    public void retryFailingMonitors() {
  //        log.info("Starting retry checks for failing monitors");
  //
  //        try {
  //            long startTime = System.currentTimeMillis();
  //
  // monitorExecutionService.executeMonitorsWithConsecutiveFailures(consecutiveFailuresThreshold);
  //            long duration = System.currentTimeMillis() - startTime;
  //
  //            log.info("Completed retry checks in {}ms", duration);
  //
  //        } catch (Exception e) {
  //            log.error("Error during retry checks: {}", e.getMessage(), e);
  //        }
  //    }

  @Scheduled(cron = "${monitor.scheduling.uptime-stats-cron:0 */15 * * * *}")
  public void calculateUptimeStats() {
    log.info("Starting uptime statistics calculation");

    try {
      long startTime = System.currentTimeMillis();
      var activeTenants = tenantService.getAllActiveTenants();

      for (var tenant : activeTenants) {
        log.debug("Calculating uptime stats for tenant: {}", tenant.getId());
        uptimeStatsService.calculateAndSaveUptimeStats(tenant.getId());
      }

      long duration = System.currentTimeMillis() - startTime;
      log.info(
          "Completed uptime statistics calculation for {} tenants in {}ms",
          activeTenants.size(),
          duration);

    } catch (Exception e) {
      log.error("Error during uptime statistics calculation: {}", e.getMessage(), e);
    }
  }

  @Scheduled(cron = "${monitor.scheduling.cleanup-cron:0 0 2 * * *}")
  public void cleanupOldData() {
    log.info("Starting cleanup of old data (retention: {} days)", retentionDays);

    try {
      long startTime = System.currentTimeMillis();
      LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
      var activeTenants = tenantService.getAllActiveTenants();

      for (var tenant : activeTenants) {
        log.debug("Cleaning up old data for tenant: {}", tenant.getId());
        checkResultService.cleanupOldCheckResults(tenant.getId(), cutoffDate);
        uptimeStatsService.cleanupOldUptimeStats(tenant.getId(), cutoffDate);
      }

      long duration = System.currentTimeMillis() - startTime;
      log.info("Completed data cleanup for {} tenants in {}ms", activeTenants.size(), duration);

    } catch (Exception e) {
      log.error("Error during data cleanup: {}", e.getMessage(), e);
    }
  }

  @Scheduled(fixedDelayString = "${monitor.scheduling.health-check-interval:30000}")
  public void healthCheck() {
    log.debug("Performing scheduler health check");

    try {
      // Simple health check to ensure scheduler is running
      long activeMonitors = monitorExecutionService.getActiveMonitorCount();
      log.debug("Scheduler health check completed - {} active monitors", activeMonitors);

    } catch (Exception e) {
      log.warn("Scheduler health check failed: {}", e.getMessage(), e);
    }
  }
}
