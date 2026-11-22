/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class CheckResultService {

  private final CheckResultRepository checkResultRepository;
  private final Clock clock;

  /** The run interval of the check job, see MonitorExecutionService. */
  private final long checkIntervalMs;

  public CheckResultService(
      CheckResultRepository checkResultRepository,
      Clock clock,
      @Value("${monitor.scheduling.check-interval:60000}") long checkIntervalMs) {
    this.checkResultRepository = checkResultRepository;
    this.clock = clock;
    this.checkIntervalMs = checkIntervalMs;
  }

  public CheckResult saveCheckResult(
      Integer tenantId, Monitor monitor, HttpClientService.HttpCheckResult httpResult) {
    return saveCheckResult(tenantId, monitor, httpResult, false);
  }

  /**
   * @param manual a check a user asked for, not due in this run: it adds no time to the uptime, it
   *     only moves time (see CheckResult.upAdjustSeconds)
   */
  public CheckResult saveCheckResult(
      Integer tenantId,
      Monitor monitor,
      HttpClientService.HttpCheckResult httpResult,
      boolean manual) {
    log.debug(
        "Saving {} check result for monitor {}: {}",
        manual ? "manual" : "scheduled",
        monitor.getId(),
        httpResult.getIsUp());
    LocalDateTime now = LocalDateTime.now(clock);

    CheckResult checkResult =
        CheckResult.builder()
            .monitor(monitor)
            .tenantId(tenantId)
            // Same clock as the uptime stats windows (UTC).
            .checkedAt(now)
            .statusCode(httpResult.getStatusCode())
            .responseTimeMs(httpResult.getResponseTimeMs())
            .isUp(httpResult.getIsUp())
            .checkInterval(
                manual
                    ? 0
                    : MonitorExecutionService.effectiveIntervalSeconds(monitor, checkIntervalMs))
            .upAdjustSeconds(manual ? upAdjustSeconds(monitor, httpResult.getIsUp(), now) : 0)
            .errorMessage(httpResult.getErrorMessage())
            .build();

    CheckResult saved = checkResultRepository.save(checkResult);
    log.debug("Check result saved with ID: {}", saved.getId());

    return saved;
  }

  /**
   * Reads the checks before a manual check, see {@link #upAdjustSeconds(CheckResult, CheckResult,
   * boolean, LocalDateTime)}.
   */
  private int upAdjustSeconds(Monitor monitor, boolean up, LocalDateTime now) {
    CheckResult lastScheduled =
        checkResultRepository
            .findTopByMonitorIdAndCheckIntervalGreaterThanOrderByCheckedAtDescIdDesc(
                monitor.getId(), 0)
            .orElse(null);
    if (lastScheduled == null) {
      return 0;
    }
    CheckResult last =
        checkResultRepository
            .findTopByMonitorIdOrderByCheckedAtDescIdDesc(monitor.getId())
            .orElse(lastScheduled);
    return upAdjustSeconds(lastScheduled, last, up, now);
  }

  /**
   * The UP seconds a manual check moves. The last scheduled check counts its time until checkedAt +
   * checkInterval. The rest of that time after now goes to the state of the manual check, away from
   * the state of the last check (a scheduled or an earlier manual check).
   *
   * <p>Example: DOWN at 10:00 with 3600 s, manual UP at 10:10: +3000. A manual DOWN at 10:20 then:
   * -2400. Sum: 600 s UP of the 3600 s, the same as the real 10 minutes UP.
   *
   * @return +rest after DOWN, -rest after UP, 0 for the same state or no time left
   */
  static int upAdjustSeconds(
      CheckResult lastScheduled, CheckResult last, boolean up, LocalDateTime now) {
    LocalDateTime coveredUntil =
        lastScheduled.getCheckedAt().plusSeconds(lastScheduled.getCheckInterval());
    long rest = java.time.Duration.between(now, coveredUntil).toSeconds();
    if (rest <= 0 || last.getIsUp() == up) {
      return 0;
    }
    int seconds = (int) Math.min(rest, lastScheduled.getCheckInterval());
    return up ? seconds : -seconds;
  }
}
