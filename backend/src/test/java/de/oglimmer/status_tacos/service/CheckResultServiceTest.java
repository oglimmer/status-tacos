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

  private static final LocalDateTime T0_LOCAL = LocalDateTime.ofInstant(T0, ZoneOffset.UTC);

  private static CheckResult check(int minutesAfterT0, boolean up, int interval) {
    return CheckResult.builder()
        .checkedAt(T0_LOCAL.plusMinutes(minutesAfterT0))
        .isUp(up)
        .checkInterval(interval)
        .build();
  }

  @Test
  void manualUpAfterScheduledDown_movesTheRestOfTheHourToUp() {
    CheckResult down = check(0, false, 3600);

    assertThat(CheckResultService.upAdjustSeconds(down, down, true, T0_LOCAL.plusMinutes(10)))
        .isEqualTo(3000);
  }

  @Test
  void manualChecksInARow_sumToTheRealUpTime() {
    CheckResult down = check(0, false, 3600);
    CheckResult manualUp = check(10, true, 0);

    int first = CheckResultService.upAdjustSeconds(down, down, true, T0_LOCAL.plusMinutes(10));
    int second =
        CheckResultService.upAdjustSeconds(down, manualUp, false, T0_LOCAL.plusMinutes(20));

    assertThat(second).isEqualTo(-2400);
    // UP from 10:10 to 10:20 only
    assertThat(first + second).isEqualTo(600);
  }

  @Test
  void manualDownAfterScheduledUp_movesTimeAwayFromUp() {
    CheckResult up = check(0, true, 3600);

    assertThat(CheckResultService.upAdjustSeconds(up, up, false, T0_LOCAL.plusMinutes(50)))
        .isEqualTo(-600);
  }

  @Test
  void manualCheckWithTheSameState_movesNothing() {
    CheckResult down = check(0, false, 3600);

    assertThat(CheckResultService.upAdjustSeconds(down, down, false, T0_LOCAL.plusMinutes(10)))
        .isZero();
  }

  @Test
  void manualCheckAfterTheTimeOfTheLastCheck_movesNothing() {
    // The scheduled check counted 15 min, the next one did not come (for example a pause)
    CheckResult down = check(0, false, 900);

    assertThat(CheckResultService.upAdjustSeconds(down, down, true, T0_LOCAL.plusMinutes(20)))
        .isZero();
  }

  @Test
  void manualCheck_savesNoIntervalAndTheAdjust() {
    when(checkResultRepository
            .findTopByMonitorIdAndCheckIntervalGreaterThanOrderByCheckedAtDescIdDesc(1, 0))
        .thenReturn(java.util.Optional.of(check(-10, false, 3600)));
    when(checkResultRepository.findTopByMonitorIdOrderByCheckedAtDescIdDesc(1))
        .thenReturn(java.util.Optional.of(check(-10, false, 3600)));
    when(checkResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CheckResultService service =
        new CheckResultService(checkResultRepository, Clock.fixed(T0, ZoneOffset.UTC), 15_000);

    CheckResult saved =
        service.saveCheckResult(
            1, Monitor.builder().id(1).checkInterval(3600).build(), httpResult, true);

    assertThat(saved.getCheckInterval()).isZero();
    assertThat(saved.getUpAdjustSeconds()).isEqualTo(3000);
  }

  @Test
  void scheduledCheck_hasNoAdjust() {
    CheckResult saved = save(Monitor.builder().id(1).checkInterval(3600).build(), 15_000);

    assertThat(saved.getUpAdjustSeconds()).isZero();
  }

  private CheckResult save(Monitor monitor, long checkIntervalMs) {
    when(checkResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CheckResultService service =
        new CheckResultService(
            checkResultRepository, Clock.fixed(T0, ZoneOffset.UTC), checkIntervalMs);
    return service.saveCheckResult(1, monitor, httpResult);
  }
}
