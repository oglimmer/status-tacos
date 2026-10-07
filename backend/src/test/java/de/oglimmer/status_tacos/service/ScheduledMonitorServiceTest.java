/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledMonitorServiceTest {

  @Mock private MonitorExecutionService monitorExecutionService;
  @Mock private CheckRollupService checkRollupService;

  @InjectMocks private ScheduledMonitorService scheduledMonitorService;

  @Test
  void executeAllMonitorChecks_swallowsErrors() {
    doThrow(new RuntimeException("boom")).when(monitorExecutionService).executeAllActiveMonitors();

    assertThatCode(() -> scheduledMonitorService.executeAllMonitorChecks())
        .doesNotThrowAnyException();
    verify(monitorExecutionService).executeAllActiveMonitors();
  }

  @Test
  void rollUpCheckResults_swallowsErrors() {
    doThrow(new RuntimeException("boom")).when(checkRollupService).rollUp();

    assertThatCode(() -> scheduledMonitorService.rollUpCheckResults()).doesNotThrowAnyException();
    verify(checkRollupService).rollUp();
  }

  @Test
  void cleanupOldData_swallowsErrors() {
    doThrow(new RuntimeException("boom")).when(checkRollupService).cleanup();

    assertThatCode(() -> scheduledMonitorService.cleanupOldData()).doesNotThrowAnyException();
    verify(checkRollupService).cleanup();
  }
}
