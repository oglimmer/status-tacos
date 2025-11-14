/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.mockito.Mockito.*;
//
// @ExtendWith(MockitoExtension.class)
// class ScheduledMonitorServiceTest {
//
//    @Mock
//    private MonitorExecutionService monitorExecutionService;
//
//    @Mock
//    private CheckResultService checkResultService;
//
//    @Mock
//    private UptimeStatsService uptimeStatsService;
//
//    @InjectMocks
//    private ScheduledMonitorService scheduledMonitorService;
//
//    @BeforeEach
//    void setUp() {
//        ReflectionTestUtils.setField(scheduledMonitorService, "retentionDays", 90);
//        ReflectionTestUtils.setField(scheduledMonitorService, "consecutiveFailuresThreshold", 3);
//    }
//
//    @Test
//    void executeAllMonitorChecks_shouldCallMonitorExecutionService() {
//        doNothing().when(monitorExecutionService).executeAllActiveMonitors();
//
//        assertThatCode(() -> scheduledMonitorService.executeAllMonitorChecks())
//                .doesNotThrowAnyException();
//
//        verify(monitorExecutionService).executeAllActiveMonitors();
//    }
//
//    @Test
//    void executeAllMonitorChecks_withException_shouldLogErrorAndContinue() {
//        doThrow(new RuntimeException("Execution failed"))
//                .when(monitorExecutionService).executeAllActiveMonitors();
//
//        assertThatCode(() -> scheduledMonitorService.executeAllMonitorChecks())
//                .doesNotThrowAnyException();
//
//        verify(monitorExecutionService).executeAllActiveMonitors();
//    }
//
//    @Test
//    void retryFailingMonitors_shouldCallMonitorExecutionServiceWithThreshold() {
//        doNothing().when(monitorExecutionService).executeMonitorsWithConsecutiveFailures(3);
//
//        assertThatCode(() -> scheduledMonitorService.retryFailingMonitors())
//                .doesNotThrowAnyException();
//
//        verify(monitorExecutionService).executeMonitorsWithConsecutiveFailures(3);
//    }
//
//    @Test
//    void retryFailingMonitors_withException_shouldLogErrorAndContinue() {
//        doThrow(new RuntimeException("Retry failed"))
//                .when(monitorExecutionService).executeMonitorsWithConsecutiveFailures(3);
//
//        assertThatCode(() -> scheduledMonitorService.retryFailingMonitors())
//                .doesNotThrowAnyException();
//
//        verify(monitorExecutionService).executeMonitorsWithConsecutiveFailures(3);
//    }
//
//    @Test
//    void calculateUptimeStats_shouldCallUptimeStatsService() {
//        doNothing().when(uptimeStatsService).calculateAndSaveUptimeStats();
//
//        assertThatCode(() -> scheduledMonitorService.calculateUptimeStats())
//                .doesNotThrowAnyException();
//
//        verify(uptimeStatsService).calculateAndSaveUptimeStats();
//    }
//
//    @Test
//    void calculateUptimeStats_withException_shouldLogErrorAndContinue() {
//        doThrow(new RuntimeException("Stats calculation failed"))
//                .when(uptimeStatsService).calculateAndSaveUptimeStats();
//
//        assertThatCode(() -> scheduledMonitorService.calculateUptimeStats())
//                .doesNotThrowAnyException();
//
//        verify(uptimeStatsService).calculateAndSaveUptimeStats();
//    }
//
//    @Test
//    void cleanupOldData_shouldCallBothCleanupServices() {
//        doNothing().when(checkResultService).cleanupOldCheckResults(any());
//        doNothing().when(uptimeStatsService).cleanupOldUptimeStats(any());
//
//        assertThatCode(() -> scheduledMonitorService.cleanupOldData())
//                .doesNotThrowAnyException();
//
//        verify(checkResultService).cleanupOldCheckResults(any());
//        verify(uptimeStatsService).cleanupOldUptimeStats(any());
//    }
//
//    @Test
//    void cleanupOldData_withException_shouldLogErrorAndContinue() {
//        doThrow(new RuntimeException("Cleanup failed"))
//                .when(checkResultService).cleanupOldCheckResults(any());
//
//        assertThatCode(() -> scheduledMonitorService.cleanupOldData())
//                .doesNotThrowAnyException();
//
//        verify(checkResultService).cleanupOldCheckResults(any());
//        verifyNoInteractions(uptimeStatsService);
//    }
//
//    @Test
//    void healthCheck_shouldCallMonitorExecutionService() {
//        when(monitorExecutionService.getActiveMonitorCount())
//                .thenReturn(5L);
//
//        assertThatCode(() -> scheduledMonitorService.healthCheck())
//                .doesNotThrowAnyException();
//
//        verify(monitorExecutionService).getActiveMonitorCount();
//    }
//
//    @Test
//    void healthCheck_withException_shouldLogWarningAndContinue() {
//        when(monitorExecutionService.getActiveMonitorCount())
//                .thenThrow(new RuntimeException("Health check failed"));
//
//        assertThatCode(() -> scheduledMonitorService.healthCheck())
//                .doesNotThrowAnyException();
//
//        verify(monitorExecutionService).getActiveMonitorCount();
//    }
//
//    @Test
//    void executeImmediateCheck_shouldCallMonitorExecutionService() {
//        doNothing().when(monitorExecutionService).executeAllActiveMonitors();
//
//        assertThatCode(() -> scheduledMonitorService.executeImmediateCheck())
//                .doesNotThrowAnyException();
//
//        verify(monitorExecutionService).executeAllActiveMonitors();
//    }
//
//    @Test
//    void executeImmediateCheck_withException_shouldThrowRuntimeException() {
//        doThrow(new RuntimeException("Execution failed"))
//                .when(monitorExecutionService).executeAllActiveMonitors();
//
//        assertThatThrownBy(() -> scheduledMonitorService.executeImmediateCheck())
//                .isInstanceOf(RuntimeException.class)
//                .hasMessageContaining("Failed to execute immediate check");
//
//        verify(monitorExecutionService).executeAllActiveMonitors();
//    }
//
//    @Test
//    void executeImmediateUptimeCalculation_shouldCallUptimeStatsService() {
//        doNothing().when(uptimeStatsService).calculateAndSaveUptimeStats();
//
//        assertThatCode(() -> scheduledMonitorService.executeImmediateUptimeCalculation())
//                .doesNotThrowAnyException();
//
//        verify(uptimeStatsService).calculateAndSaveUptimeStats();
//    }
//
//    @Test
//    void executeImmediateUptimeCalculation_withException_shouldThrowRuntimeException() {
//        doThrow(new RuntimeException("Calculation failed"))
//                .when(uptimeStatsService).calculateAndSaveUptimeStats();
//
//        assertThatThrownBy(() -> scheduledMonitorService.executeImmediateUptimeCalculation())
//                .isInstanceOf(RuntimeException.class)
//                .hasMessageContaining("Failed to execute immediate uptime calculation");
//
//        verify(uptimeStatsService).calculateAndSaveUptimeStats();
//    }
// }
