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
import org.mockito.InjectMocks;
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

  @Mock private ApplicationContext applicationContext;

  @InjectMocks private MonitorExecutionService monitorExecutionService;

  private Monitor testMonitor;
  private HttpClientService.HttpCheckResult successfulHttpResult;
  private HttpClientService.HttpCheckResult failedHttpResult;
  private CheckResult testCheckResult;
  private MonitorStatus testMonitorStatus;
  private Tenant testTenant;
  private static final Integer TEST_TENANT_ID = 1;

  @BeforeEach
  void setUp() {
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
  void executeMonitorsWithConsecutiveFailures_withNoFailingMonitors_shouldReturnEarly() {
    when(tenantService.getAllActiveTenants()).thenReturn(List.of(testTenant));
    when(monitorStatusService.getMonitorsWithConsecutiveFailures(TEST_TENANT_ID, 3))
        .thenReturn(List.of());

    monitorExecutionService.executeMonitorsWithConsecutiveFailures(3);

    verify(tenantService).getAllActiveTenants();
    verify(monitorStatusService).getMonitorsWithConsecutiveFailures(TEST_TENANT_ID, 3);
    verifyNoInteractions(httpClientService);
    verifyNoInteractions(checkResultService);
  }

  @Test
  void executeMonitorsWithConsecutiveFailures_withFailingMonitors_shouldRetryChecks() {
    MonitorStatus failingStatus =
        MonitorStatus.builder()
            .monitorId(1)
            .monitor(testMonitor)
            .tenantId(TEST_TENANT_ID)
            .currentStatus(MonitorStatus.StatusType.down)
            .consecutiveFailures(3)
            .build();

    when(tenantService.getAllActiveTenants()).thenReturn(List.of(testTenant));
    when(monitorStatusService.getMonitorsWithConsecutiveFailures(TEST_TENANT_ID, 3))
        .thenReturn(List.of(failingStatus));

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
            eq(testMonitor.getUrl()),
            eq(null),
            eq("^[23]\\d{2}$"),
            eq(null),
            eq(null),
            eq(null),
            eq(null)))
        .thenReturn(successfulHttpResult);
    when(checkResultService.saveCheckResult(eq(TEST_TENANT_ID), any(), any()))
        .thenReturn(testCheckResult);
    when(monitorStatusService.updateMonitorStatus(eq(TEST_TENANT_ID), any(), any()))
        .thenReturn(testMonitorStatus);

    monitorExecutionService.executeMonitorsWithConsecutiveFailures(3);

    verify(tenantService).getAllActiveTenants();
    verify(monitorStatusService).getMonitorsWithConsecutiveFailures(TEST_TENANT_ID, 3);
    verify(httpClientService)
        .performHealthCheck(
            eq(testMonitor.getUrl()),
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
  void getActiveMonitorCount_shouldReturnCountFromMonitorService() {
    when(tenantService.getAllActiveTenants()).thenReturn(List.of(testTenant));
    when(monitorService.getActiveMonitorCount(TEST_TENANT_ID)).thenReturn(10L);

    long result = monitorExecutionService.getActiveMonitorCount();

    assertThat(result).isEqualTo(10L);
    verify(tenantService).getAllActiveTenants();
    verify(monitorService).getActiveMonitorCount(TEST_TENANT_ID);
  }
}
