/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.MonitorStatus;
import de.oglimmer.status_tacos.persistence.Tenant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class MonitorExecutionService {

  private final HttpClientService httpClientService;
  private final CheckResultService checkResultService;
  private final MonitorStatusService monitorStatusService;
  private final MonitorService monitorService;
  private final TenantService tenantService;
  private final AlertService alertService;
  private final Executor taskExecutor;
  private final ApplicationContext applicationContext;

  // Self-reference for @Transactional proxy to work
  private MonitorExecutionService self;

  public MonitorExecutionService(
      HttpClientService httpClientService,
      CheckResultService checkResultService,
      MonitorStatusService monitorStatusService,
      MonitorService monitorService,
      TenantService tenantService,
      AlertService alertService,
      Executor taskExecutor,
      ApplicationContext applicationContext) {
    this.httpClientService = httpClientService;
    this.checkResultService = checkResultService;
    this.monitorStatusService = monitorStatusService;
    this.monitorService = monitorService;
    this.tenantService = tenantService;
    this.alertService = alertService;
    this.taskExecutor = taskExecutor;
    this.applicationContext = applicationContext;
  }

  private MonitorExecutionService getSelf() {
    if (self == null) {
      // Lazy initialization to avoid circular dependency during bean creation
      self = applicationContext.getBean(MonitorExecutionService.class);
    }
    return self;
  }

  public CheckResult executeMonitorCheck(Monitor monitor) {
    log.debug(
        "Executing check for monitor: {} ({}) for tenant: {}",
        monitor.getName(),
        monitor.getUrl(),
        monitor.getTenantId());

    try {
      // Perform HTTP check WITHOUT holding a database transaction
      HttpClientService.HttpCheckResult httpResult =
          httpClientService.performHealthCheck(
              monitor.getUrl(),
              monitor.getHttpHeaders(),
              monitor.getStatusCodeRegex(),
              monitor.getResponseBodyRegex(),
              monitor.getPrometheusKey(),
              monitor.getPrometheusMinValue(),
              monitor.getPrometheusMaxValue());

      // Save results in a separate transaction (using self-reference for proxy)
      CheckResult checkResult = getSelf().saveCheckResultAndUpdateStatus(monitor, httpResult);

      log.info(
          "Monitor check completed for {}: status={}, responseTime={}ms",
          monitor.getName(),
          httpResult.getIsUp() ? "UP" : "DOWN",
          httpResult.getResponseTimeMs());

      return checkResult;

    } catch (Exception e) {
      log.error("Error executing monitor check for {}: {}", monitor.getName(), e.getMessage(), e);

      HttpClientService.HttpCheckResult errorHttpResult =
          createErrorHttpResult(monitor.getUrl(), e.getMessage());
      CheckResult savedResult = getSelf().saveCheckResultAndUpdateStatus(monitor, errorHttpResult);

      return savedResult;
    }
  }

  @Transactional
  protected CheckResult saveCheckResultAndUpdateStatus(
      Monitor monitor, HttpClientService.HttpCheckResult httpResult) {
    // Save check result and update status in a single transaction
    CheckResult checkResult =
        checkResultService.saveCheckResult(monitor.getTenantId(), monitor, httpResult);
    MonitorStatus updatedStatus =
        monitorStatusService.updateMonitorStatus(monitor.getTenantId(), monitor, checkResult);

    // Handle alerts based on status - only send alerts if monitor is ACTIVE
    if (monitor.getState() == MonitorState.ACTIVE) {
      if (!httpResult.getIsUp()) {
        // Monitor is down - check if it has been down for at least the alerting threshold
        if (shouldSendDownAlert(monitor, updatedStatus)) {
          alertService.handleMonitorDown(
              monitor, httpResult.getStatusCode() != null ? httpResult.getStatusCode() : 0);
        } else {
          log.debug(
              "Monitor {} is down but hasn't exceeded alerting threshold of {}s yet",
              monitor.getName(),
              monitor.getAlertingThreshold());
        }
      } else {
        // Monitor is up, send recovery alert if needed
        alertService.handleMonitorUp(monitor);
      }
    } else {
      log.debug(
          "Monitor {} is in {} state, skipping alert notifications",
          monitor.getName(),
          monitor.getState());
    }

    return checkResult;
  }

  public void executeAllActiveMonitors() {
    log.debug("Starting execution of all active monitors");

    // Get all active tenants and their monitors
    List<Tenant> activeTenants = tenantService.getAllActiveTenants();
    if (activeTenants.isEmpty()) {
      log.info("No active tenants found");
      return;
    }

    Set<Integer> activeTenantIds =
        activeTenants.stream().map(Tenant::getId).collect(Collectors.toSet());

    log.debug("Found {} active tenants: {}", activeTenants.size(), activeTenantIds);

    // Get both ACTIVE and SILENT monitors (both should be monitored)
    List<Monitor> activeMonitors =
        monitorService.getMonitorsByState(activeTenantIds, MonitorState.ACTIVE);
    List<Monitor> silentMonitors =
        monitorService.getMonitorsByState(activeTenantIds, MonitorState.SILENT);

    List<Monitor> monitorsToCheck = new java.util.ArrayList<>(activeMonitors);
    monitorsToCheck.addAll(silentMonitors);

    if (monitorsToCheck.isEmpty()) {
      log.info("No monitors to check found");
      return;
    }

    log.debug(
        "Found {} monitors to check ({} active, {} silent)",
        monitorsToCheck.size(),
        activeMonitors.size(),
        silentMonitors.size());

    List<CompletableFuture<Void>> futures =
        monitorsToCheck.stream()
            .map(
                monitor ->
                    CompletableFuture.runAsync(() -> executeMonitorCheck(monitor), taskExecutor)
                        .exceptionally(
                            throwable -> {
                              log.error(
                                  "Failed to execute monitor check for {}: {}",
                                  monitor.getName(),
                                  throwable.getMessage(),
                                  throwable);
                              return null;
                            }))
            .toList();

    CompletableFuture<Void> allChecks =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

    try {
      allChecks.join();
      log.debug("Completed execution of all active monitors");
    } catch (Exception e) {
      log.error("Error during monitor execution batch: {}", e.getMessage(), e);
    }
  }

  public CompletableFuture<CheckResult> executeMonitorCheckAsync(Monitor monitor) {
    log.debug("Executing async check for monitor: {}", monitor.getName());

    return CompletableFuture.supplyAsync(() -> executeMonitorCheck(monitor), taskExecutor)
        .exceptionally(
            throwable -> {
              log.error(
                  "Async monitor check failed for {}: {}",
                  monitor.getName(),
                  throwable.getMessage(),
                  throwable);

              return checkResultService.saveCheckResult(
                  monitor.getTenantId(),
                  monitor,
                  createErrorHttpResult(monitor.getUrl(), throwable.getMessage()));
            });
  }

  public void executeMonitorsWithConsecutiveFailures(int threshold) {
    log.info("Executing monitors with {} or more consecutive failures", threshold);

    // Get all active tenants
    List<Tenant> activeTenants = tenantService.getAllActiveTenants();
    if (activeTenants.isEmpty()) {
      log.info("No active tenants found");
      return;
    }

    Set<Integer> activeTenantIds =
        activeTenants.stream().map(Tenant::getId).collect(Collectors.toSet());

    // Get failing monitors from all active tenants
    List<MonitorStatus> failingMonitors =
        activeTenantIds.stream()
            .flatMap(
                tenantId ->
                    monitorStatusService
                        .getMonitorsWithConsecutiveFailures(tenantId, threshold)
                        .stream())
            .toList();

    if (failingMonitors.isEmpty()) {
      log.info("No monitors found with {} or more consecutive failures", threshold);
      return;
    }

    log.info(
        "Found {} monitors with consecutive failures >= {}", failingMonitors.size(), threshold);

    List<CompletableFuture<Void>> futures =
        failingMonitors.stream()
            .map(status -> status.getMonitor())
            .map(
                monitor ->
                    CompletableFuture.runAsync(() -> executeMonitorCheck(monitor), taskExecutor)
                        .exceptionally(
                            throwable -> {
                              log.error(
                                  "Failed retry check for monitor {}: {}",
                                  monitor.getName(),
                                  throwable.getMessage(),
                                  throwable);
                              return null;
                            }))
            .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    log.info("Completed retry checks for failing monitors");
  }

  public long getActiveMonitorCount() {
    // Get all active tenants and count their active monitors
    List<Tenant> activeTenants = tenantService.getAllActiveTenants();
    if (activeTenants.isEmpty()) {
      return 0;
    }

    return activeTenants.stream()
        .mapToLong(tenant -> monitorService.getActiveMonitorCount(tenant.getId()))
        .sum();
  }

  /**
   * Determines if a down alert should be sent for a monitor based on the alerting threshold. Only
   * sends alert if the monitor has been down for at least alertingThreshold seconds.
   */
  private boolean shouldSendDownAlert(Monitor monitor, MonitorStatus status) {
    // If monitor just went down (lastDownAt is null or very recent), don't send alert yet
    if (status.getLastDownAt() == null) {
      log.debug("Monitor {} lastDownAt is null, skipping alert", monitor.getName());
      return false;
    }

    // Calculate how long the monitor has been down
    LocalDateTime now = LocalDateTime.now();
    long secondsDown = ChronoUnit.SECONDS.between(status.getLastDownAt(), now);

    // Only send alert if monitor has been down for at least the alerting threshold
    boolean shouldAlert = secondsDown >= monitor.getAlertingThreshold();

    log.debug(
        "Monitor {} has been down for {}s (threshold: {}s), shouldAlert: {}",
        monitor.getName(),
        secondsDown,
        monitor.getAlertingThreshold(),
        shouldAlert);

    return shouldAlert;
  }

  private HttpClientService.HttpCheckResult createErrorHttpResult(String url, String errorMessage) {
    return HttpClientService.HttpCheckResult.builder()
        .url(url)
        .statusCode(null)
        .responseTimeMs(0)
        .isUp(false)
        .errorMessage(errorMessage)
        .responseBody(null)
        .build();
  }
}
