/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.AlertHistoryRepository;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.MonitorStatusRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class MonitorMoveServiceTest {

  private static final Integer SOURCE_TENANT_ID = 1;
  private static final Integer TARGET_TENANT_ID = 2;

  @Mock private MonitorRepository monitorRepository;
  @Mock private CheckResultRepository checkResultRepository;
  @Mock private AlertHistoryRepository alertHistoryRepository;
  @Mock private MonitorStatusRepository monitorStatusRepository;
  @Mock private AlertContactRepository alertContactRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private MonitorMoveService monitorMoveService;

  private Monitor monitor;
  private Tenant targetTenant;

  @BeforeEach
  void setUp() {
    monitor =
        Monitor.builder()
            .id(1)
            .name("Test Monitor")
            .url("https://example.com")
            .state(MonitorState.ACTIVE)
            .tenantId(SOURCE_TENANT_ID)
            .build();
    targetTenant = Tenant.builder().id(TARGET_TENANT_ID).name("Target").build();
    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
  }

  private void stubValidMove() {
    when(monitorRepository.findById(1)).thenReturn(Optional.of(monitor));
    when(monitorRepository.findByIdForUpdate(1)).thenReturn(Optional.of(monitor));
    when(tenantRepository.findById(TARGET_TENANT_ID)).thenReturn(Optional.of(targetTenant));
    when(monitorRepository.findByTenantIdAndUrlIgnoreCase(TARGET_TENANT_ID, monitor.getUrl()))
        .thenReturn(Optional.empty());
    when(monitorRepository.save(monitor)).thenReturn(monitor);
  }

  @Test
  void moveMonitorToTenant_shouldMoveOldCheckResultsInBatchesAndRestInFinalStep() {
    stubValidMove();
    when(checkResultRepository.findOldestCheckedAt(1))
        .thenReturn(Optional.of(LocalDateTime.now().minusDays(3)));
    when(alertContactRepository.findScopedToMonitor(1)).thenReturn(List.of());

    Monitor result =
        monitorMoveService.moveMonitorToTenant(Set.of(SOURCE_TENANT_ID), 1, TARGET_TENANT_ID);

    assertThat(result.getTenantId()).isEqualTo(TARGET_TENANT_ID);
    assertThat(result.getTenant()).isSameAs(targetTenant);
    // 3 days of old check results in 1-day ranges (3 or 4 ranges), plus 1 range in the final step
    verify(checkResultRepository, atLeast(4))
        .updateTenantIdByMonitorIdInRange(eq(1), eq(TARGET_TENANT_ID), any(), any());
    verify(checkResultRepository, atMost(5))
        .updateTenantIdByMonitorIdInRange(eq(1), eq(TARGET_TENANT_ID), any(), any());
    verify(alertHistoryRepository).updateTenantIdByMonitorId(1, TARGET_TENANT_ID);
    verify(monitorStatusRepository).updateTenantIdByMonitorId(1, TARGET_TENANT_ID);
  }

  @Test
  void moveMonitorToTenant_shouldRetryBatchOnLockConflict() {
    stubValidMove();
    when(checkResultRepository.findOldestCheckedAt(1))
        .thenReturn(Optional.of(LocalDateTime.now().minusHours(12)));
    when(alertContactRepository.findScopedToMonitor(1)).thenReturn(List.of());
    when(checkResultRepository.updateTenantIdByMonitorIdInRange(
            eq(1), eq(TARGET_TENANT_ID), any(), any()))
        .thenThrow(new CannotAcquireLockException("Record has changed since last read"))
        .thenReturn(100);

    Monitor result =
        monitorMoveService.moveMonitorToTenant(Set.of(SOURCE_TENANT_ID), 1, TARGET_TENANT_ID);

    assertThat(result.getTenantId()).isEqualTo(TARGET_TENANT_ID);
    // 1 batch range (failed once, then retried) plus 1 range in the final step
    verify(checkResultRepository, times(3))
        .updateTenantIdByMonitorIdInRange(eq(1), eq(TARGET_TENANT_ID), any(), any());
  }

  @Test
  void moveMonitorToTenant_shouldRemoveMonitorFromScopedAlertContacts() {
    stubValidMove();
    when(checkResultRepository.findOldestCheckedAt(1)).thenReturn(Optional.empty());
    Monitor otherMonitor = Monitor.builder().id(5).tenantId(SOURCE_TENANT_ID).build();
    AlertContact onlyThisMonitor =
        AlertContact.builder()
            .id(10)
            .allMonitors(false)
            .monitors(new HashSet<>(Set.of(monitor)))
            .build();
    AlertContact twoMonitors =
        AlertContact.builder()
            .id(11)
            .allMonitors(false)
            .monitors(new HashSet<>(Set.of(monitor, otherMonitor)))
            .build();
    when(alertContactRepository.findScopedToMonitor(1))
        .thenReturn(List.of(onlyThisMonitor, twoMonitors));

    monitorMoveService.moveMonitorToTenant(Set.of(SOURCE_TENANT_ID), 1, TARGET_TENANT_ID);

    verify(alertContactRepository).delete(onlyThisMonitor);
    verify(alertContactRepository, never()).delete(twoMonitors);
    assertThat(twoMonitors.getMonitors()).containsExactly(otherMonitor);
  }

  @Test
  void moveMonitorToTenant_withMonitorOfOtherTenant_shouldThrowNotFound() {
    when(monitorRepository.findById(1)).thenReturn(Optional.of(monitor));

    assertThatThrownBy(
            () -> monitorMoveService.moveMonitorToTenant(Set.of(99), 1, TARGET_TENANT_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Monitor not found with ID: 1");

    verifyNoInteractions(checkResultRepository, alertContactRepository);
  }

  @Test
  void moveMonitorToTenant_withSameTenant_shouldThrow() {
    when(monitorRepository.findById(1)).thenReturn(Optional.of(monitor));

    assertThatThrownBy(
            () ->
                monitorMoveService.moveMonitorToTenant(
                    Set.of(SOURCE_TENANT_ID), 1, SOURCE_TENANT_ID))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(checkResultRepository);
    verify(monitorRepository, never()).save(any(Monitor.class));
  }

  @Test
  void moveMonitorToTenant_withUrlUsedInTargetTenant_shouldThrowBeforeMovingAnything() {
    when(monitorRepository.findById(1)).thenReturn(Optional.of(monitor));
    when(tenantRepository.findById(TARGET_TENANT_ID)).thenReturn(Optional.of(targetTenant));
    when(monitorRepository.findByTenantIdAndUrlIgnoreCase(TARGET_TENANT_ID, monitor.getUrl()))
        .thenReturn(Optional.of(Monitor.builder().id(7).build()));

    assertThatThrownBy(
            () ->
                monitorMoveService.moveMonitorToTenant(
                    Set.of(SOURCE_TENANT_ID), 1, TARGET_TENANT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already exists in target tenant");

    verifyNoInteractions(checkResultRepository, alertContactRepository);
    verify(monitorRepository, never()).save(any(Monitor.class));
  }
}
