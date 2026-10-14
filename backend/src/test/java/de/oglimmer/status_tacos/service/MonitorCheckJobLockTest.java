/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.oglimmer.status_tacos.config.TestSecurityConfig;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The lock works through the Spring proxy of the job bean. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class MonitorCheckJobLockTest {

  @MockitoBean private LockProvider lockProvider;
  @MockitoBean private MonitorExecutionService monitorExecutionService;

  @Autowired private MonitorCheckJob monitorCheckJob;

  @Test
  void withoutTheLockTheJobRunsNoChecks() {
    when(lockProvider.lock(any())).thenReturn(Optional.empty());

    monitorCheckJob.run();

    verifyNoInteractions(monitorExecutionService);
  }

  @Test
  void withTheLockTheJobRunsTheChecksAndReleasesTheLock() {
    SimpleLock lock = mock(SimpleLock.class);
    when(lockProvider.lock(any())).thenReturn(Optional.of(lock));

    monitorCheckJob.run();

    verify(monitorExecutionService).executeAllActiveMonitors();
    verify(lock).unlock();
    ArgumentCaptor<LockConfiguration> config = ArgumentCaptor.forClass(LockConfiguration.class);
    verify(lockProvider).lock(config.capture());
    assertThat(config.getValue().getName()).isEqualTo("monitor-checks");
  }
}
