/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import de.oglimmer.status_tacos.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MonitorExecutionServiceTest {

  @Mock private HttpClientService httpClientService;

  @Mock private CheckResultService checkResultService;

  @Mock private MonitorStatusService monitorStatusService;

  @Mock private MonitorService monitorService;

  @Mock private TenantService tenantService;

  @Mock private AlertService alertService;

  @Mock private Executor taskExecutor;

  @Mock private Executor alertExecutor;

  @Mock private ApplicationContext applicationContext;

  private MonitorExecutionService monitorExecutionService;

  private Monitor testMonitor;
  private HttpClientService.HttpCheckResult successfulHttpResult;
  private HttpClientService.HttpCheckResult failedHttpResult;
  private CheckResult testCheckResult;
  private MonitorStatus testMonitorStatus;
  private Tenant testTenant;
  private static final Integer TEST_TENANT_ID = 1;

  @BeforeEach
  void setUp() {
    monitorExecutionService =
        new MonitorExecutionService(
            httpClientService,
            checkResultService,
            monitorStatusService,
            monitorService,
            tenantService,
            alertService,
            taskExecutor,
            alertExecutor,
            applicationContext,
            0L); // no start spread: checks start at once

    // Mock applicationContext.getBean() to return the service instance for self-reference
    // Use lenient() because not all tests use this stubbing
    lenient()
        .when(applicationContext.getBean(MonitorExecutionService.class))
        .thenReturn(monitorExecutionService);

    // Set the self field using reflection since constructor already ran
    ReflectionTestUtils.setField(monitorExecutionService, "self", monitorExecutionService);

    testTenant =
        Tenant.builder()
            .id(TEST_TENANT_ID)
            .name("Test Tenant")
            .code("TEST")
            .description("Test tenant")
            .isActive(true)
            .build();

    testMonitor =
        Monitor.builder()
            .id(1)
            .name("Test Monitor")
            .url("https://example.com")
            .state(MonitorState.ACTIVE)
            .tenantId(TEST_TENANT_ID)
            .build();

    successfulHttpResult =
        HttpClientService.HttpCheckResult.builder()
            .url("https://example.com")
            .statusCode(200)
            .responseTimeMs(150)
            .isUp(true)
            .errorMessage(null)
            .build();

    failedHttpResult =
        HttpClientService.HttpCheckResult.builder()
            .url("https://example.com")
            .statusCode(500)
            .responseTimeMs(200)
            .isUp(false)
            .errorMessage("HTTP 500 response")
            .build();

    testCheckResult =
        CheckResult.builder()
            .id(1L)
            .monitor(testMonitor)
            .tenantId(TEST_TENANT_ID)
            .checkedAt(LocalDateTime.now())
            .statusCode(200)
            .responseTimeMs(150)
            .isUp(true)
            .build();

    testMonitorStatus =
        MonitorStatus.builder()
            .monitorId(1)
            .monitor(testMonitor)
            .tenantId(TEST_TENANT_ID)
            .currentStatus(MonitorStatus.StatusType.up)
            .consecutiveFailures(0)
            .build();
  }

  @Test
  void executeMonitorCheck_withSuccessfulResult_shouldSaveResultAndUpdateStatus() {
    when(httpClientService.performHealthCheck(
            eq(testMonitor.getUrl()),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null)))
        .thenReturn(successfulHttpResult);
    when(checkResultService.saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any()))
        .thenReturn(testCheckResult);
    when(monitorStatusService.updateMonitorStatus(
            eq(TEST_TENANT_ID), eq(testMonitor), eq(testCheckResult)))
        .thenReturn(testMonitorStatus);

    CheckResult result = monitorExecutionService.executeMonitorCheck(testMonitor);

    assertThat(result).isNotNull();
    assertThat(result.getIsUp()).isTrue();

    verify(httpClientService)
        .performHealthCheck(
            eq(testMonitor.getUrl()),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null));
    verify(checkResultService).saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any());
    verify(monitorStatusService)
        .updateMonitorStatus(eq(TEST_TENANT_ID), eq(testMonitor), eq(testCheckResult));
  }

  @Test
  void executeMonitorCheck_withFailedResult_shouldSaveFailureAndUpdateStatus() {
    when(httpClientService.performHealthCheck(
            eq(testMonitor.getUrl()),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null)))
        .thenReturn(failedHttpResult);

    CheckResult failedCheckResult =
        CheckResult.builder()
            .id(2L)
            .monitor(testMonitor)
            .tenantId(TEST_TENANT_ID)
            .checkedAt(LocalDateTime.now())
            .statusCode(500)
            .responseTimeMs(200)
            .isUp(false)
            .errorMessage("HTTP 500 response")
            .build();

    MonitorStatus failedStatus =
        MonitorStatus.builder()
            .monitorId(1)
            .monitor(testMonitor)
            .tenantId(TEST_TENANT_ID)
            .currentStatus(MonitorStatus.StatusType.down)
            .consecutiveFailures(1)
            .build();

    when(checkResultService.saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any()))
        .thenReturn(failedCheckResult);
    when(monitorStatusService.updateMonitorStatus(
            eq(TEST_TENANT_ID), eq(testMonitor), eq(failedCheckResult)))
        .thenReturn(failedStatus);

    CheckResult result = monitorExecutionService.executeMonitorCheck(testMonitor);

    assertThat(result).isNotNull();
    assertThat(result.getIsUp()).isFalse();

    verify(httpClientService)
        .performHealthCheck(
            eq(testMonitor.getUrl()),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null));
    verify(checkResultService).saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any());
    verify(monitorStatusService)
        .updateMonitorStatus(eq(TEST_TENANT_ID), eq(testMonitor), eq(failedCheckResult));
  }

  @Test
  void executeMonitorCheck_withException_shouldHandleErrorGracefully() {
    when(httpClientService.performHealthCheck(
            eq(testMonitor.getUrl()),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null)))
        .thenThrow(new RuntimeException("Network timeout"));

    CheckResult errorCheckResult =
        CheckResult.builder()
            .id(3L)
            .monitor(testMonitor)
            .tenantId(TEST_TENANT_ID)
            .checkedAt(LocalDateTime.now())
            .statusCode(null)
            .responseTimeMs(0)
            .isUp(false)
            .errorMessage("Internal error: Network timeout")
            .build();

    when(checkResultService.saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any()))
        .thenReturn(errorCheckResult);
    when(monitorStatusService.updateMonitorStatus(eq(TEST_TENANT_ID), eq(testMonitor), any()))
        .thenReturn(testMonitorStatus);

    CheckResult result = monitorExecutionService.executeMonitorCheck(testMonitor);

    assertThat(result).isNotNull();
    assertThat(result.getIsUp()).isFalse();
    assertThat(result.getErrorMessage()).contains("Internal error");

    verify(httpClientService)
        .performHealthCheck(
            eq(testMonitor.getUrl()),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null));
    verify(checkResultService).saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any());
    verify(monitorStatusService).updateMonitorStatus(eq(TEST_TENANT_ID), eq(testMonitor), any());
  }

  @Test
  void executeMonitorCheck_whenSavingFails_doesNotStoreADownResult() {
    when(httpClientService.performHealthCheck(
            eq(testMonitor.getUrl()),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null)))
        .thenReturn(successfulHttpResult);
    // For example no free database connection
    when(checkResultService.saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any()))
        .thenThrow(new RuntimeException("Could not open JPA EntityManager for transaction"));

    CheckResult result = monitorExecutionService.executeMonitorCheck(testMonitor);

    assertThat(result).isNull();
    // Only the one attempt with the real (UP) result, no second attempt with a DOWN result
    verify(checkResultService, times(1))
        .saveCheckResult(
            eq(TEST_TENANT_ID),
            eq(testMonitor),
            argThat(HttpClientService.HttpCheckResult::getIsUp));
    verify(checkResultService, times(1)).saveCheckResult(any(), any(), any());
    verifyNoInteractions(monitorStatusService, alertService);
  }

  @Test
  void executeAllActiveMonitors_withNoActiveMonitors_shouldReturnEarly() {
    when(tenantService.getAllActiveTenants()).thenReturn(List.of(testTenant));
    when(monitorService.getMonitorsByState(Set.of(TEST_TENANT_ID), MonitorState.ACTIVE))
        .thenReturn(List.of());
    when(monitorService.getMonitorsByState(Set.of(TEST_TENANT_ID), MonitorState.SILENT))
        .thenReturn(List.of());

    monitorExecutionService.executeAllActiveMonitors();

    verify(tenantService).getAllActiveTenants();
    verify(monitorService).getMonitorsByState(Set.of(TEST_TENANT_ID), MonitorState.ACTIVE);
    verify(monitorService).getMonitorsByState(Set.of(TEST_TENANT_ID), MonitorState.SILENT);
    verifyNoInteractions(httpClientService);
    verifyNoInteractions(checkResultService);
    verifyNoInteractions(monitorStatusService);
  }

  @Test
  void executeAllActiveMonitors_withActiveMonitors_shouldExecuteAllChecks() {
    Monitor activeMonitor =
        Monitor.builder()
            .id(1)
            .name("Test Monitor")
            .url("https://example.com")
            .state(MonitorState.ACTIVE)
            .tenantId(TEST_TENANT_ID)
            .build();

    when(tenantService.getAllActiveTenants()).thenReturn(List.of(testTenant));
    when(monitorService.getMonitorsByState(Set.of(TEST_TENANT_ID), MonitorState.ACTIVE))
        .thenReturn(List.of(activeMonitor));
    when(monitorService.getMonitorsByState(Set.of(TEST_TENANT_ID), MonitorState.SILENT))
        .thenReturn(List.of());

    // Mock taskExecutor to run synchronously for testing
    doAnswer(
            invocation -> {
              Runnable task = invocation.getArgument(0);
              task.run();
              return null;
            })
        .when(taskExecutor)
        .execute(any(Runnable.class));

    when(httpClientService.performHealthCheck(
            anyString(), any(), any(), any(), any(), any(), any()))
        .thenReturn(successfulHttpResult);
    when(checkResultService.saveCheckResult(eq(TEST_TENANT_ID), any(), any()))
        .thenReturn(testCheckResult);
    when(monitorStatusService.updateMonitorStatus(eq(TEST_TENANT_ID), any(), any()))
        .thenReturn(testMonitorStatus);

    monitorExecutionService.executeAllActiveMonitors();

    verify(tenantService).getAllActiveTenants();
    verify(monitorService).getMonitorsByState(Set.of(TEST_TENANT_ID), MonitorState.ACTIVE);
    verify(monitorService).getMonitorsByState(Set.of(TEST_TENANT_ID), MonitorState.SILENT);
    verify(httpClientService)
        .performHealthCheck(
            eq("https://example.com"),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null));
    verify(checkResultService).saveCheckResult(eq(TEST_TENANT_ID), any(), any());
    verify(monitorStatusService).updateMonitorStatus(eq(TEST_TENANT_ID), any(), any());
  }

  @Test
  void alertsAreQueuedAfterTheSave_notSentInsideIt() {
    stubCheck(failedHttpResult, downCheck(), downStatusSince(120));
    testMonitor.setAlertingThreshold(30);

    monitorExecutionService.executeMonitorCheck(testMonitor);

    // Saved, but nothing sent yet: the alert waits on the alert thread
    verify(checkResultService).saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any());
    verifyNoInteractions(alertService);
    runQueuedAlert();
    verify(alertService)
        .handleMonitorDown(eq(testMonitor), any(), eq(downStatusSince(120).getOutageStartedAt()));
  }

  @Test
  void downAlertWaitsForTheAlertingThreshold() {
    stubCheck(failedHttpResult, downCheck(), downStatusSince(10));
    testMonitor.setAlertingThreshold(30);

    monitorExecutionService.executeMonitorCheck(testMonitor);

    verifyNoInteractions(alertExecutor, alertService);
  }

  @Test
  void upCheckQueuesTheRecoveryAlertWithTheOutageStart() {
    MonitorStatus recovered = recoveredStatus();
    stubCheck(successfulHttpResult, testCheckResult, recovered);

    monitorExecutionService.executeMonitorCheck(testMonitor);
    runQueuedAlert();

    verify(alertService)
        .handleMonitorUp(testMonitor, testCheckResult, recovered.getOutageStartedAt());
  }

  @Test
  void upCheckLongAfterTheLastFailureQueuesNoRecoveryAlert() {
    MonitorStatus longUp = recoveredStatus();
    longUp.setLastDownAt(testCheckResult.getCheckedAt().minusHours(2));
    stubCheck(successfulHttpResult, testCheckResult, longUp);

    monitorExecutionService.executeMonitorCheck(testMonitor);

    verifyNoInteractions(alertExecutor, alertService);
  }

  @Test
  void upCheckOfAMonitorThatWasNeverDownQueuesNoRecoveryAlert() {
    stubCheck(successfulHttpResult, testCheckResult, testMonitorStatus);

    monitorExecutionService.executeMonitorCheck(testMonitor);

    verifyNoInteractions(alertExecutor, alertService);
  }

  @Test
  void startOffsetsAreSpreadOverTheWindow() {
    long spread = 3_750;
    java.util.Set<Long> offsets = new java.util.HashSet<>();
    for (int id = 1; id <= 100; id++) {
      long offset = MonitorExecutionService.startOffsetMs(id, spread);
      assertThat(offset).isBetween(0L, spread - 1);
      offsets.add(offset);
    }
    // Fixed per monitor, and different monitors start at different times
    assertThat(MonitorExecutionService.startOffsetMs(7, spread))
        .isEqualTo(MonitorExecutionService.startOffsetMs(7, spread));
    assertThat(offsets).hasSizeGreaterThan(90);
    assertThat(MonitorExecutionService.startOffsetMs(7, 0)).isZero();
  }

  @Test
  void silentMonitorQueuesNoAlert() {
    testMonitor.setState(MonitorState.SILENT);
    stubCheck(failedHttpResult, downCheck(), downStatusSince(600));

    monitorExecutionService.executeMonitorCheck(testMonitor);

    verifyNoInteractions(alertExecutor, alertService);
  }

  @Test
  void failingAlertDoesNotBreakTheCheck() {
    stubCheck(successfulHttpResult, testCheckResult, recoveredStatus());
    doThrow(new RuntimeException("SMTP down"))
        .when(alertService)
        .handleMonitorUp(any(), any(), any());

    CheckResult result = monitorExecutionService.executeMonitorCheck(testMonitor);
    runQueuedAlert();

    assertThat(result).isSameAs(testCheckResult);
  }

  private void stubCheck(
      HttpClientService.HttpCheckResult httpResult, CheckResult saved, MonitorStatus status) {
    when(httpClientService.performHealthCheck(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(httpResult);
    when(checkResultService.saveCheckResult(eq(TEST_TENANT_ID), eq(testMonitor), any()))
        .thenReturn(saved);
    when(monitorStatusService.updateMonitorStatus(TEST_TENANT_ID, testMonitor, saved))
        .thenReturn(status);
  }

  private void runQueuedAlert() {
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(alertExecutor).execute(task.capture());
    task.getValue().run();
  }

  private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 10, 6, 10, 0, 0);

  private CheckResult downCheck() {
    return CheckResult.builder()
        .id(2L)
        .monitor(testMonitor)
        .tenantId(TEST_TENANT_ID)
        .checkedAt(CHECKED_AT)
        .statusCode(500)
        .isUp(false)
        .build();
  }

  /** UP again, the last failed check was 15 seconds before this check. */
  private MonitorStatus recoveredStatus() {
    MonitorStatus status = downStatusSince(300);
    status.setCurrentStatus(MonitorStatus.StatusType.up);
    status.setLastDownAt(testCheckResult.getCheckedAt().minusSeconds(15));
    return status;
  }

  private MonitorStatus downStatusSince(int secondsAgo) {
    return MonitorStatus.builder()
        .monitorId(1)
        .tenantId(TEST_TENANT_ID)
        .currentStatus(MonitorStatus.StatusType.down)
        .outageStartedAt(CHECKED_AT.minusSeconds(secondsAgo))
        .consecutiveFailures(3)
        .build();
  }

  @Test
  void getActiveMonitorCount_shouldReturnCountFromMonitorService() {
    when(tenantService.getAllActiveTenants()).thenReturn(List.of(testTenant));
    when(monitorService.getActiveMonitorCount(TEST_TENANT_ID)).thenReturn(10L);

    long result = monitorExecutionService.getActiveMonitorCount();

    assertThat(result).isEqualTo(10L);
    verify(tenantService).getAllActiveTenants();
    verify(monitorService).getActiveMonitorCount(TEST_TENANT_ID);
  }
}
