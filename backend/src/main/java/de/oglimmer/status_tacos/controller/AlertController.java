/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.AlertResponseDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorStatus;
import de.oglimmer.status_tacos.service.MonitorService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {

  private final MonitorService monitorService;
  private final UserTenantResolver userTenantResolver;

  @GetMapping
  public ResponseEntity<List<AlertResponseDto>> getAlerts() {
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug("Getting alerts for tenants: {}", tenantIds);

    List<Monitor> monitors = monitorService.getAllMonitorsWithStatusAndTenant(tenantIds);

    List<AlertResponseDto> alerts = monitors.stream().map(this::mapToAlertResponse).toList();

    return ResponseEntity.ok(alerts);
  }

  private AlertResponseDto mapToAlertResponse(Monitor monitor) {
    MonitorStatus status = monitor.getMonitorStatus();
    String statusValue = status != null ? status.getCurrentStatus().name() : "unknown";

    return AlertResponseDto.builder()
        .monitorName(monitor.getName())
        .tenantName(monitor.getTenant().getName())
        .status(statusValue)
        .downtimeStart(
            status != null && status.getCurrentStatus() == MonitorStatus.StatusType.down
                ? status.getLastDownAt()
                : null)
        .build();
  }
}
