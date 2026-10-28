/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** check_results.check_interval: the weight of a check in the uptime. */
@ExtendWith(MockitoExtension.class)
class CheckResultServiceTest {

  private static final Instant T0 = Instant.parse("2026-10-06T10:00:00Z");

  @Mock private CheckResultRepository checkResultRepository;

  private final HttpClientService.HttpCheckResult httpResult =
      HttpClientService.HttpCheckResult.builder()
          .url("https://example.com")
          .statusCode(200)
          .responseTimeMs(150)
          .isUp(true)
          .build();

  @Test
  void savesTheIntervalOfTheMonitor() {
    CheckResult saved = save(Monitor.builder().id(1).checkInterval(3600).build(), 15_000);

    assertThat(saved.getCheckInterval()).isEqualTo(3600);
    assertThat(saved.getCheckedAt()).isEqualTo(LocalDateTime.ofInstant(T0, ZoneOffset.UTC));
  }

  @Test
  void savesTheIntervalTheJobReallyRuns() {
    // The job runs every 60 s, so a 15 s monitor is checked every 60 s
    CheckResult saved = save(Monitor.builder().id(1).checkInterval(15).build(), 60_000);

    assertThat(saved.getCheckInterval()).isEqualTo(60);
  }

  private CheckResult save(Monitor monitor, long checkIntervalMs) {
    when(checkResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CheckResultService service =
        new CheckResultService(
            checkResultRepository, Clock.fixed(T0, ZoneOffset.UTC), checkIntervalMs);
    return service.saveCheckResult(1, monitor, httpResult);
  }
}
