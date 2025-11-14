/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.MonitorStatusResponseDto;
import de.oglimmer.status_tacos.dto.ResponseTimeHistoryResponseDto;
import de.oglimmer.status_tacos.dto.StatusType;
import de.oglimmer.status_tacos.mapper.EntityMapper;
import de.oglimmer.status_tacos.persistence.MonitorStatus;
import de.oglimmer.status_tacos.service.CheckResultService;
import de.oglimmer.status_tacos.service.MonitorStatusService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/monitor-statuses")
@RequiredArgsConstructor
@Slf4j
public class MonitorStatusController {

  private final MonitorStatusService monitorStatusService;
  private final CheckResultService checkResultService;
  private final UserTenantResolver userTenantResolver;
  private final EntityMapper entityMapper;

  @GetMapping
  public ResponseEntity<List<MonitorStatusResponseDto>> getAllActiveMonitorStatuses() {
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug("Getting all active monitor statuses for tenants: {}", tenantIds);

    List<MonitorStatus> statuses =
        tenantIds.stream()
            .flatMap(
                tenantId -> monitorStatusService.getAllActiveMonitorStatuses(tenantId).stream())
            .toList();
    List<MonitorStatusResponseDto> dtos =
        statuses.stream().map(this::convertToDto).collect(Collectors.toList());

    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/{monitorId}/response-time-history-24h")
  public ResponseEntity<ResponseTimeHistoryResponseDto> getResponseTimeHistory24h(
      @PathVariable Integer monitorId) {
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug(
        "Getting 24h response time history for monitor: {} across tenants: {}",
        monitorId,
        tenantIds);

    // Find the tenant that owns this monitor
    for (Integer tenantId : tenantIds) {
      ResponseTimeHistoryResponseDto history =
          checkResultService.getResponseTimeHistory24h(tenantId, monitorId);
      if (!history.getDataPoints().isEmpty()
          || history.getMonitorName() != null && !history.getMonitorName().equals("Unknown")) {
        return ResponseEntity.ok(history);
      }
    }

    // If no data found in any tenant, return empty response for the first tenant
    Integer tenantId = tenantIds.iterator().next();
    ResponseTimeHistoryResponseDto emptyHistory =
        checkResultService.getResponseTimeHistory24h(tenantId, monitorId);
    return ResponseEntity.ok(emptyHistory);
  }

  private MonitorStatusResponseDto convertToDto(MonitorStatus status) {
    MonitorStatusResponseDto dto = entityMapper.toDto(status);
    dto.setCurrentStatus(convertToDtoStatusType(status.getCurrentStatus()));
    return dto;
  }

  private StatusType convertToDtoStatusType(MonitorStatus.StatusType persistenceStatusType) {
    return StatusType.valueOf(persistenceStatusType.name());
  }
}
