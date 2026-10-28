/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.MonitorStatus;
import de.oglimmer.status_tacos.persistence.Tenant;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class MonitorExecutionService {

  /**
   * An UP check looks for a DOWN alert without UP alert only this long after the last failed check
   * (or two check intervals of the monitor, if that is longer). Within this time a failed UP alert
   * is tried again at each check.
   */
  static final Duration RECOVERY_ALERT_WINDOW = Duration.ofMinutes(10);

  private final HttpClientService httpClientService;
  private final CheckResultService checkResultService;
  private final MonitorStatusService monitorStatusService;
  private final MonitorService monitorService;
  private final TenantService tenantService;
  private final AlertService alertService;
  private final Executor taskExecutor;
  private final Executor alertExecutor;
  private final ApplicationContext applicationContext;
  private final Clock clock;

  /** The run interval of the check job. The check interval of a monitor is a multiple of it. */
  private final long checkIntervalMs;

  private final long totalTimeoutMs;

  /** The checks of one run start spread over this time, not all at the same moment. */
  private final long startSpreadMs;

  // Self-reference for @Transactional proxy to work
  private MonitorExecutionService self;

  public MonitorExecutionService(
      HttpClientService httpClientService,
      CheckResultService checkResultService,
      MonitorStatusService monitorStatusService,
      MonitorService monitorService,
      TenantService tenantService,
      AlertService alertService,
      @Qualifier("taskExecutor") Executor taskExecutor,
      @Qualifier("alertExecutor") Executor alertExecutor,
      ApplicationContext applicationContext,
      Clock clock,
      @Value("${monitor.scheduling.check-interval:60000}") long checkIntervalMs,
      @Value("${monitor.http.total-timeout:30s}") Duration totalTimeout) {
    this.httpClientService = httpClientService;
    this.checkResultService = checkResultService;
    this.monitorStatusService = monitorStatusService;
    this.monitorService = monitorService;
    this.tenantService = tenantService;
    this.alertService = alertService;
    this.taskExecutor = taskExecutor;
    this.alertExecutor = alertExecutor;
    this.applicationContext = applicationContext;
    this.clock = clock;
    this.checkIntervalMs = checkIntervalMs;
    this.totalTimeoutMs = totalTimeout.toMillis();
    // A quarter of the interval: the run (spread + slowest check) must end before the next one
    this.startSpreadMs = checkIntervalMs / 4;
  }

  /**
   * A run takes the start spread plus the slowest check, and the slowest check takes the total
   * timeout. The run must end before the next run starts: the scheduler fires at a fixed rate but
   * never two runs at once, so a run that is too long moves every monitor away from its interval.
   */
  @PostConstruct
  void validateTiming() {
    long longestRunMs = startSpreadMs + totalTimeoutMs;
    if (longestRunMs >= checkIntervalMs) {
      throw new IllegalStateException(
          "A run of all checks can take "
              + longestRunMs
              + " ms (start spread "
              + startSpreadMs
              + " ms, a quarter of monitor.scheduling.check-interval, plus monitor.http.total-timeout "
              + totalTimeoutMs
              + " ms) but must end before the next run starts after monitor.scheduling.check-interval "
              + checkIntervalMs
              + " ms");
    }
  }

  private MonitorExecutionService getSelf() {
    if (self == null) {
      // Lazy initialization to avoid circular dependency during bean creation
      self = applicationContext.getBean(MonitorExecutionService.class);
    }
    return self;
  }

  /**
   * Checks the monitor, saves the result and then queues the alerts.
   *
   * <p>An error of the HTTP check counts as DOWN: the monitored service did not answer correctly.
   * An error while saving the result (for example no free database connection) is our own problem,
   * not the monitored service's. It is only logged, so it never shows as downtime.
   *
   * <p>Alerts are sent after the commit, on the alert thread. A slow mail server or webhook then
   * does not hold the transaction open: the result commits at once (the roll-up job expects that)
   * and the check does not keep a database connection or the lock on the monitor row.
   *
   * @return the saved result, or null if it could not be saved
   */
  public CheckResult executeMonitorCheck(Monitor monitor) {
    log.debug(
        "Executing check for monitor: {} ({}) for tenant: {}",
        monitor.getName(),
        monitor.getUrl(),
        monitor.getTenantId());

    // Perform HTTP check WITHOUT holding a database transaction
    HttpClientService.HttpCheckResult httpResult;
    try {
      httpResult =
          httpClientService.performHealthCheck(
              monitor.getUrl(),
              monitor.getHttpHeaders(),
              monitor.getStatusCodeRegex(),
              monitor.getResponseBodyRegex(),
              monitor.getPrometheusKey(),
              monitor.getPrometheusMinValue(),
              monitor.getPrometheusMaxValue(),
              monitor.getConnectTimeout() != null
                  ? Duration.ofSeconds(monitor.getConnectTimeout())
                  : null);
    } catch (Exception e) {
      log.error("Error executing monitor check for {}: {}", monitor.getName(), e.getMessage(), e);
      httpResult = createErrorHttpResult(monitor.getUrl(), e.getMessage());
    }

    SavedCheck saved;
    try {
      // Save results in a separate transaction (using self-reference for proxy)
      saved = getSelf().saveCheckResultAndUpdateStatus(monitor, httpResult);
    } catch (Exception e) {
      log.error(
          "Could not save check result for monitor {}, result dropped: {}",
          monitor.getName(),
          e.getMessage(),
          e);
      return null;
    }
    log.debug(
        "Monitor check completed for {}: status={}, responseTime={}ms",
        monitor.getName(),
        httpResult.getIsUp() ? "UP" : "DOWN",
        httpResult.getResponseTimeMs());

    queueAlerts(monitor, saved.checkResult(), saved.status());
    return saved.checkResult();
  }

  /** The saved check and the monitor status after it. */
  record SavedCheck(CheckResult checkResult, MonitorStatus status) {}

  @Transactional
  protected SavedCheck saveCheckResultAndUpdateStatus(
      Monitor monitor, HttpClientService.HttpCheckResult httpResult) {
    // The monitor was loaded before the HTTP check. Its tenant can have changed (tenant move), so
    // read the current tenant. The shared lock waits for a running move to commit.
    monitorService.lockTenantId(monitor.getId()).ifPresent(monitor::setTenantId);

    // Save check result and update status in a single transaction
    CheckResult checkResult =
        checkResultService.saveCheckResult(monitor.getTenantId(), monitor, httpResult);
    MonitorStatus status =
        monitorStatusService.updateMonitorStatus(monitor.getTenantId(), monitor, checkResult);
    return new SavedCheck(checkResult, status);
  }

  /** Only ACTIVE monitors send alerts. Runs after the commit. */
  private void queueAlerts(Monitor monitor, CheckResult checkResult, MonitorStatus status) {
    if (monitor.getState() != MonitorState.ACTIVE) {
      log.debug(
          "Monitor {} is in {} state, skipping alert notifications",
          monitor.getName(),
          monitor.getState());
      return;
    }
    LocalDateTime outageStart = status != null ? status.getOutageStartedAt() : null;

    Runnable alert;
    if (!checkResult.getIsUp()) {
      // Monitor is down - check if it has been down for at least the alerting threshold
      if (!shouldSendDownAlert(monitor, checkResult, outageStart)) {
        return;
      }
      alert = () -> alertService.handleMonitorDown(monitor, checkResult, outageStart);
    } else {
      // Monitor is up, send recovery alert if needed. A monitor that was not down in the last
      // minutes can have no open DOWN alert, so the alert thread does not need to look.
      if (!recentlyDown(monitor, status, checkResult)) {
        return;
      }
      alert = () -> alertService.handleMonitorUp(monitor, checkResult, outageStart);
    }

    try {
      alertExecutor.execute(
          () -> {
            try {
              alert.run();
            } catch (Exception e) {
              log.error(
                  "Could not send alerts for monitor {}: {}", monitor.getName(), e.getMessage(), e);
            }
          });
    } catch (RejectedExecutionException e) {
      log.error("Alert queue is full, alert for monitor {} dropped", monitor.getName());
    }
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

    List<Monitor> monitors = new java.util.ArrayList<>(activeMonitors);
    monitors.addAll(silentMonitors);

    if (monitors.isEmpty()) {
      log.info("No monitors to check found");
      return;
    }

    long slot = slotOf(clock.millis(), checkIntervalMs);
    List<Monitor> monitorsToCheck =
        monitors.stream().filter(monitor -> isDue(monitor, slot, checkIntervalMs)).toList();

    log.debug(
        "{} of {} monitors are due in slot {} ({} active, {} silent)",
        monitorsToCheck.size(),
        monitors.size(),
        slot,
        activeMonitors.size(),
        silentMonitors.size());

    List<CompletableFuture<Void>> futures =
        monitorsToCheck.stream()
            .map(
                monitor ->
                    CompletableFuture.runAsync(
                            () -> executeMonitorCheck(monitor), startExecutor(monitor))
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

  /**
   * Starts the check after the fixed offset of the monitor, so each monitor keeps its interval. The
   * delay runs on the one JDK delay thread, which only queues the check: the queue of taskExecutor
   * has no limit, so the thread is never held by a check.
   */
  private Executor startExecutor(Monitor monitor) {
    long offset = startOffsetMs(monitor.getId(), startSpreadMs);
    return offset > 0
        ? CompletableFuture.delayedExecutor(offset, TimeUnit.MILLISECONDS, taskExecutor)
        : taskExecutor;
  }

  /** A fixed offset in [0, spreadMs) per monitor. Neighbouring ids get offsets far apart. */
  static long startOffsetMs(Integer monitorId, long spreadMs) {
    if (spreadMs <= 0 || monitorId == null) {
      return 0;
    }
    // Fibonacci hashing spreads consecutive ids evenly
    return Math.floorMod(monitorId * 0x9E3779B97F4A7C15L, spreadMs);
  }

  /**
   * The number of the run that starts at this time. The trigger fires at the multiples of the
   * interval (CheckGridTrigger), so rounding to the nearest multiple leaves half an interval for a
   * late start or a replica clock that is a little off.
   */
  static long slotOf(long epochMs, long checkIntervalMs) {
    return checkIntervalMs > 0 ? Math.floorDiv(epochMs + checkIntervalMs / 2, checkIntervalMs) : 0;
  }

  /**
   * A monitor with an interval of n runs is checked in every n-th slot. The slot in the cycle comes
   * from the id, so the monitors with a long interval are spread over the runs. The slot comes from
   * the clock, not from the last check, so a change of replica does not move the checks.
   */
  static boolean isDue(Monitor monitor, long slot, long checkIntervalMs) {
    if (monitor.getCheckInterval() == null || checkIntervalMs <= 0 || monitor.getId() == null) {
      return true;
    }
    long runs = runsPerCheck(monitor, checkIntervalMs);
    return runs == 1
        || Math.floorMod(slot, runs) == Math.floorMod(monitor.getId() * 0x9E3779B97F4A7C15L, runs);
  }

  /**
   * The seconds between two checks of the monitor, as the check job really runs them (see isDue).
   * This is the weight of a check in the uptime.
   */
  static int effectiveIntervalSeconds(Monitor monitor, long checkIntervalMs) {
    if (checkIntervalMs <= 0) {
      return monitor.getCheckInterval() != null ? monitor.getCheckInterval() : 15;
    }
    long runs = monitor.getCheckInterval() == null ? 1 : runsPerCheck(monitor, checkIntervalMs);
    return (int) Math.max(1, runs * checkIntervalMs / 1000);
  }

  private static long runsPerCheck(Monitor monitor, long checkIntervalMs) {
    // An interval that is no multiple of the run interval rounds down: never fewer checks
    return Math.max(1, monitor.getCheckInterval() * 1000L / checkIntervalMs);
  }

  private static boolean recentlyDown(
      Monitor monitor, MonitorStatus status, CheckResult checkResult) {
    Duration window = RECOVERY_ALERT_WINDOW;
    if (monitor.getCheckInterval() != null) {
      // The next check after the last failed one must still be in the window
      Duration twoIntervals = Duration.ofSeconds(2L * monitor.getCheckInterval());
      if (twoIntervals.compareTo(window) > 0) {
        window = twoIntervals;
      }
    }
    return status != null
        && status.getLastDownAt() != null
        && !status.getLastDownAt().isBefore(checkResult.getCheckedAt().minus(window));
  }

  public CompletableFuture<CheckResult> executeMonitorCheckAsync(Monitor monitor) {
    log.debug("Executing async check for monitor: {}", monitor.getName());

    return CompletableFuture.supplyAsync(() -> executeMonitorCheck(monitor), taskExecutor)
        .exceptionally(
            throwable -> {
              // Not a DOWN result: executeMonitorCheck already stores HTTP errors as DOWN, so this
              // is an error of our own (for example the executor).
              log.error(
                  "Async monitor check failed for {}: {}",
                  monitor.getName(),
                  throwable.getMessage(),
                  throwable);
              return null;
            });
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
   * sends alert if the monitor has been down for at least alertingThreshold seconds, measured from
   * the first failed check of the outage (MonitorStatus.outageStartedAt). MonitorStatus.lastDownAt
   * is the latest failed check, so it can not be used: it is always about 0 seconds old.
   */
  private boolean shouldSendDownAlert(
      Monitor monitor, CheckResult checkResult, LocalDateTime outageStart) {
    if (outageStart == null) {
      log.debug("Monitor {} has no outage start, skipping alert", monitor.getName());
      return false;
    }

    long secondsDown = ChronoUnit.SECONDS.between(outageStart, checkResult.getCheckedAt());

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
