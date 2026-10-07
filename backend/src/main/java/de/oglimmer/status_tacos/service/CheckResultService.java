/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CheckResultService {

  private final CheckResultRepository checkResultRepository;
  private final Clock clock;

  public CheckResult saveCheckResult(
      Integer tenantId, Monitor monitor, HttpClientService.HttpCheckResult httpResult) {
    log.debug("Saving check result for monitor {}: {}", monitor.getId(), httpResult.getIsUp());

    CheckResult checkResult =
        CheckResult.builder()
            .monitor(monitor)
            .tenantId(tenantId)
            // Same clock as the uptime stats windows (UTC).
            .checkedAt(LocalDateTime.now(clock))
            .statusCode(httpResult.getStatusCode())
            .responseTimeMs(httpResult.getResponseTimeMs())
            .isUp(httpResult.getIsUp())
            .errorMessage(httpResult.getErrorMessage())
            .build();

    CheckResult saved = checkResultRepository.save(checkResult);
    log.debug("Check result saved with ID: {}", saved.getId());

    return saved;
  }
}
