/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.oglimmer.status_tacos.dto.MonitorRequestDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

  @Mock private MonitorRepository monitorRepository;

  @InjectMocks private MonitorService monitorService;

  private Monitor testMonitor;
  private MonitorRequestDto testRequestDto;
  private static final Integer TEST_TENANT_ID = 1;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();

    testMonitor =
        Monitor.builder()
            .id(1)
            .name("Test Monitor")
            .url("https://example.com")
            .state(MonitorState.ACTIVE)
            .tenantId(TEST_TENANT_ID)
            .createdAt(now)
            .updatedAt(now)
            .build();

    testRequestDto =
        MonitorRequestDto.builder()
            .name("Test Monitor")
            .url("https://example.com")
            .state(MonitorState.ACTIVE)
            .build();
  }

  @Test
  void createMonitor_withValidData_shouldCreateMonitor() {
    when(monitorRepository.findByTenantIdAndUrlIgnoreCase(TEST_TENANT_ID, testRequestDto.getUrl()))
        .thenReturn(Optional.empty());
    when(monitorRepository.save(any(Monitor.class))).thenReturn(testMonitor);

    Monitor result = monitorService.createMonitor(TEST_TENANT_ID, testRequestDto);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(testRequestDto.getName());
    assertThat(result.getUrl()).isEqualTo(testRequestDto.getUrl());

    verify(monitorRepository)
        .findByTenantIdAndUrlIgnoreCase(TEST_TENANT_ID, testRequestDto.getUrl());
    verify(monitorRepository).save(any(Monitor.class));
  }

  @Test
  void createMonitor_withDuplicateUrl_shouldThrowException() {
    when(monitorRepository.findByTenantIdAndUrlIgnoreCase(TEST_TENANT_ID, testRequestDto.getUrl()))
        .thenReturn(Optional.of(testMonitor));

    assertThatThrownBy(() -> monitorService.createMonitor(TEST_TENANT_ID, testRequestDto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Monitor with URL already exists");

    verify(monitorRepository)
        .findByTenantIdAndUrlIgnoreCase(TEST_TENANT_ID, testRequestDto.getUrl());
    verify(monitorRepository, never()).save(any(Monitor.class));
  }

  @Test
  void getMonitorById_withExistingId_shouldReturnMonitor() {
    when(monitorRepository.findByIdAndTenantId(1, TEST_TENANT_ID))
        .thenReturn(Optional.of(testMonitor));

    Monitor result = monitorService.getMonitorById(TEST_TENANT_ID, 1);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(1);
    assertThat(result.getName()).isEqualTo(testMonitor.getName());

    verify(monitorRepository).findByIdAndTenantId(1, TEST_TENANT_ID);
  }

  @Test
  void getMonitorById_withNonExistingId_shouldThrowException() {
    when(monitorRepository.findByIdAndTenantId(999, TEST_TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> monitorService.getMonitorById(TEST_TENANT_ID, 999))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Monitor not found with ID: 999");

    verify(monitorRepository).findByIdAndTenantId(999, TEST_TENANT_ID);
  }

  @Test
  void getAllMonitors_shouldReturnAllMonitors() {
    List<Monitor> monitors = List.of(testMonitor);
    when(monitorRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(monitors);

    List<Monitor> result = monitorService.getAllMonitors(TEST_TENANT_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(testMonitor.getId());

    verify(monitorRepository).findByTenantId(TEST_TENANT_ID);
  }

  @Test
  void getAllMonitors_withPageable_shouldReturnPagedResult() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<Monitor> monitorPage = new PageImpl<>(List.of(testMonitor), pageable, 1);

    when(monitorRepository.findAll(pageable)).thenReturn(monitorPage);

    Page<Monitor> result = monitorService.getAllMonitors(TEST_TENANT_ID, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo(testMonitor.getId());

    verify(monitorRepository).findAll(pageable);
  }

  @Test
  void getActiveMonitors_shouldReturnOnlyActiveMonitors() {
    List<Monitor> activeMonitors = List.of(testMonitor);
    when(monitorRepository.findByTenantIdAndState(TEST_TENANT_ID, MonitorState.ACTIVE))
        .thenReturn(activeMonitors);

    List<Monitor> result = monitorService.getActiveMonitors(TEST_TENANT_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getState()).isEqualTo(MonitorState.ACTIVE);

    verify(monitorRepository).findByTenantIdAndState(TEST_TENANT_ID, MonitorState.ACTIVE);
  }

  @Test
  void updateMonitor_withValidData_shouldUpdateMonitor() {
    when(monitorRepository.findByIdAndTenantId(1, TEST_TENANT_ID))
        .thenReturn(Optional.of(testMonitor));
    when(monitorRepository.save(any(Monitor.class))).thenReturn(testMonitor);

    Monitor result = monitorService.updateMonitor(TEST_TENANT_ID, 1, testRequestDto);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(1);

    verify(monitorRepository).findByIdAndTenantId(1, TEST_TENANT_ID);
    verify(monitorRepository).save(any(Monitor.class));
  }

  @Test
  void updateMonitor_withNonExistingId_shouldThrowException() {
    when(monitorRepository.findByIdAndTenantId(999, TEST_TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> monitorService.updateMonitor(TEST_TENANT_ID, 999, testRequestDto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Monitor not found with ID: 999");

    verify(monitorRepository).findByIdAndTenantId(999, TEST_TENANT_ID);
    verify(monitorRepository, never()).save(any(Monitor.class));
  }

  @Test
  void deleteMonitor_withExistingId_shouldDeleteMonitor() {
    when(monitorRepository.findByIdAndTenantId(1, TEST_TENANT_ID))
        .thenReturn(Optional.of(testMonitor));

    monitorService.deleteMonitor(TEST_TENANT_ID, 1);

    verify(monitorRepository).findByIdAndTenantId(1, TEST_TENANT_ID);
    verify(monitorRepository).delete(testMonitor);
  }

  @Test
  void deleteMonitor_withNonExistingId_shouldThrowException() {
    when(monitorRepository.findByIdAndTenantId(999, TEST_TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> monitorService.deleteMonitor(TEST_TENANT_ID, 999))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Monitor not found with ID: 999");

    verify(monitorRepository).findByIdAndTenantId(999, TEST_TENANT_ID);
    verify(monitorRepository, never()).delete(any(Monitor.class));
  }

  @Test
  void toggleMonitorStatus_shouldChangeActiveStatus() {
    Monitor inactiveMonitor =
        Monitor.builder()
            .id(1)
            .name("Test Monitor")
            .url("https://example.com")
            .state(MonitorState.INACTIVE)
            .tenantId(TEST_TENANT_ID)
            .build();

    Monitor toggledMonitor =
        Monitor.builder()
            .id(1)
            .name("Test Monitor")
            .url("https://example.com")
            .state(MonitorState.ACTIVE)
            .tenantId(TEST_TENANT_ID)
            .build();

    when(monitorRepository.findByIdAndTenantId(1, TEST_TENANT_ID))
        .thenReturn(Optional.of(inactiveMonitor));
    when(monitorRepository.save(any(Monitor.class))).thenReturn(toggledMonitor);

    Monitor result = monitorService.updateMonitorState(TEST_TENANT_ID, 1, MonitorState.ACTIVE);

    assertThat(result.getState()).isEqualTo(MonitorState.ACTIVE);

    verify(monitorRepository).findByIdAndTenantId(1, TEST_TENANT_ID);
    verify(monitorRepository).save(any(Monitor.class));
  }

  @Test
  void getActiveMonitorCount_shouldReturnCount() {
    when(monitorRepository.countByTenantIdAndState(TEST_TENANT_ID, MonitorState.ACTIVE))
        .thenReturn(5L);

    long result = monitorService.getActiveMonitorCount(TEST_TENANT_ID);

    assertThat(result).isEqualTo(5L);
    verify(monitorRepository).countByTenantIdAndState(TEST_TENANT_ID, MonitorState.ACTIVE);
  }

  @Test
  void searchMonitorsByName_shouldReturnMatchingMonitors() {
    List<Monitor> foundMonitors = List.of(testMonitor);
    when(monitorRepository.findByTenantIdAndNameContainingIgnoreCase(TEST_TENANT_ID, "Test"))
        .thenReturn(foundMonitors);

    List<Monitor> result = monitorService.searchMonitorsByName(TEST_TENANT_ID, "Test");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).contains("Test");

    verify(monitorRepository).findByTenantIdAndNameContainingIgnoreCase(TEST_TENANT_ID, "Test");
  }
}
