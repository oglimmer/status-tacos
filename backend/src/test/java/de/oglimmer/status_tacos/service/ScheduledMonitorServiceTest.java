/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledMonitorServiceTest {

  @Mock private MonitorCheckJob monitorCheckJob;
  @Mock private CheckRollupService checkRollupService;
  @Mock private SchedulerHeartbeat heartbeat;

  @InjectMocks private ScheduledMonitorService scheduledMonitorService;

  @Test
  void executeAllMonitorChecks_recordsTheHeartbeatBeforeTheJob() {
    scheduledMonitorService.executeAllMonitorChecks();

    // The heartbeat comes first: the job is skipped on a replica without the lock
    InOrder inOrder = inOrder(heartbeat, monitorCheckJob);
    inOrder.verify(heartbeat).checksFired();
    inOrder.verify(monitorCheckJob).run();
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
