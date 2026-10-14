/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorCheckJobTest {

  @Mock private MonitorExecutionService monitorExecutionService;
  @Mock private SchedulerHeartbeat heartbeat;

  @InjectMocks private MonitorCheckJob job;

  @Test
  void run_recordsTheEndOfTheRun() {
    job.run();

    InOrder inOrder = inOrder(monitorExecutionService, heartbeat);
    inOrder.verify(monitorExecutionService).executeAllActiveMonitors();
    inOrder.verify(heartbeat).checksFinished(any(Duration.class));
  }

  @Test
  void run_swallowsErrorsAndRecordsNoRun() {
    doThrow(new RuntimeException("boom")).when(monitorExecutionService).executeAllActiveMonitors();

    assertThatCode(() -> job.run()).doesNotThrowAnyException();
    verifyNoInteractions(heartbeat);
  }
}
