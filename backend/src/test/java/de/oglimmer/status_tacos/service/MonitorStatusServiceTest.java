/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorStatus;
import de.oglimmer.status_tacos.repository.MonitorStatusRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** monitor_status.outage_started_at: the first failed check of the current or last outage. */
@ExtendWith(MockitoExtension.class)
class MonitorStatusServiceTest {

  private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 10, 0, 0);

  @Mock private MonitorStatusRepository monitorStatusRepository;
  @InjectMocks private MonitorStatusService monitorStatusService;

  private final Monitor monitor = Monitor.builder().id(1).name("Shop").tenantId(1).build();
  private MonitorStatus stored;

  @BeforeEach
  void setUp() {
    when(monitorStatusRepository.findByMonitorIdAndTenantId(1, 1))
        .thenAnswer(invocation -> Optional.ofNullable(stored));
    when(monitorStatusRepository.save(any()))
        .thenAnswer(
            invocation -> {
              stored = invocation.getArgument(0);
              return stored;
            });
  }

  @Test
  void firstFailedCheckStartsTheOutage() {
    check(true, T0);
    check(false, T0.plusSeconds(15));
    MonitorStatus status = check(false, T0.plusSeconds(30));

    assertThat(status.getOutageStartedAt()).isEqualTo(T0.plusSeconds(15));
    assertThat(status.getConsecutiveFailures()).isEqualTo(2);
  }

  @Test
  void upCheckKeepsTheStartOfTheOutageThatJustEnded() {
    check(false, T0);
    check(false, T0.plusSeconds(15));
    MonitorStatus status = check(true, T0.plusSeconds(30));

    assertThat(status.getCurrentStatus()).isEqualTo(MonitorStatus.StatusType.up);
    assertThat(status.getOutageStartedAt()).isEqualTo(T0);
  }

  @Test
  void nextOutageGetsANewStart() {
    check(false, T0);
    check(true, T0.plusSeconds(15));
    MonitorStatus status = check(false, T0.plusSeconds(30));

    assertThat(status.getOutageStartedAt()).isEqualTo(T0.plusSeconds(30));
  }

  @Test
  void monitorThatWasNeverUpIsDownSinceItsFirstCheck() {
    check(false, T0);
    MonitorStatus status = check(false, T0.plusSeconds(15));

    assertThat(status.getOutageStartedAt()).isEqualTo(T0);
  }

  @Test
  void downRowWithoutStartGetsOne() {
    // A row from before the column, which the migration could not fill
    stored =
        MonitorStatus.builder()
            .monitorId(1)
            .tenantId(1)
            .currentStatus(MonitorStatus.StatusType.down)
            .consecutiveFailures(5)
            .build();

    MonitorStatus status = check(false, T0);

    assertThat(status.getOutageStartedAt()).isEqualTo(T0);
  }

  private MonitorStatus check(boolean up, LocalDateTime checkedAt) {
    CheckResult result =
        CheckResult.builder().monitor(monitor).tenantId(1).isUp(up).checkedAt(checkedAt).build();
    return monitorStatusService.updateMonitorStatus(1, monitor, result);
  }
}
