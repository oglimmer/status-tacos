/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.MonitorRequestDto;
import de.oglimmer.status_tacos.dto.MonitorResponseDto;
import de.oglimmer.status_tacos.mapper.EntityMapper;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.service.MonitorService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/monitors")
@RequiredArgsConstructor
@Slf4j
public class MonitorController {

  private final MonitorService monitorService;
  private final UserTenantResolver userTenantResolver;
  private final EntityMapper entityMapper;

  @PostMapping
  public ResponseEntity<MonitorResponseDto> createMonitor(
      @Valid @RequestBody MonitorRequestDto requestDto) {
    // Validate that the user has access to the requested tenant
    if (!userTenantResolver.hasAccessToTenant(requestDto.getTenantId())) {
      log.warn(
          "User attempted to create monitor for unauthorized tenant: {}", requestDto.getTenantId());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    log.info("Creating monitor: {} for tenant: {}", requestDto.getName(), requestDto.getTenantId());

    try {
      Monitor monitor = monitorService.createMonitor(requestDto.getTenantId(), requestDto);
      MonitorResponseDto responseDto = entityMapper.toDto(monitor);
      return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    } catch (IllegalArgumentException e) {
      log.warn("Failed to create monitor: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    }
  }

  //    @GetMapping("/{id}")
  //    public ResponseEntity<MonitorResponseDto> getMonitor(@PathVariable Integer id) {
  //        Integer tenantId = userTenantResolver.getCurrentUserTenantId();
  //        log.debug("Getting monitor by ID: {} for tenant: {}", id, tenantId);
  //
  //        try {
  //            Monitor monitor = monitorService.getMonitorById(tenantId, id);
  //            MonitorResponseDto responseDto = entityMapper.toDto(monitor);
  //            return ResponseEntity.ok(responseDto);
  //        } catch (IllegalArgumentException e) {
  //            log.warn("Monitor not found: {}", e.getMessage());
  //            return ResponseEntity.notFound().build();
  //        }
  //    }

  @GetMapping
  public ResponseEntity<List<MonitorResponseDto>> getAllMonitors(
      @RequestParam(required = false) String name,
      @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {

    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug(
        "Getting monitors - name: {}, activeOnly: {} for tenants: {}", name, activeOnly, tenantIds);

    List<Monitor> monitors;

    if (name != null && !name.trim().isEmpty()) {
      monitors = monitorService.searchMonitorsByName(tenantIds, name.trim());
    } else if (activeOnly) {
      monitors = monitorService.getActiveMonitors(tenantIds);
    } else {
      monitors = monitorService.getAllMonitors(tenantIds);
    }

    List<MonitorResponseDto> responseDtos = monitors.stream().map(entityMapper::toDto).toList();

    return ResponseEntity.ok(responseDtos);
  }

  @PutMapping("/{id}")
  public ResponseEntity<MonitorResponseDto> updateMonitor(
      @PathVariable Integer id, @Valid @RequestBody MonitorRequestDto requestDto) {

    // First validate that the user has access to the tenant they're trying to assign
    if (!userTenantResolver.hasAccessToTenant(requestDto.getTenantId())) {
      log.warn(
          "User attempted to update monitor to unauthorized tenant: {}", requestDto.getTenantId());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      Monitor monitor = monitorService.updateMonitor(requestDto.getTenantId(), id, requestDto);
      MonitorResponseDto responseDto = entityMapper.toDto(monitor);
      return ResponseEntity.ok(responseDto);
    } catch (IllegalArgumentException e) {
      log.debug("Monitor {} not found in tenant {}", id, requestDto.getTenantId());
    }

    log.warn("Failed to update monitor: {} - not found in any accessible tenant", id);
    return ResponseEntity.notFound().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteMonitor(@PathVariable Integer id) {
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.info("Deleting monitor ID: {} for tenants: {}", id, tenantIds);

    boolean deleted = false;
    for (Integer tenantId : tenantIds) {
      try {
        monitorService.deleteMonitor(tenantId, id);
        deleted = true;
        break;
      } catch (IllegalArgumentException e) {
        log.debug("Monitor {} not found in tenant {}", id, tenantId);
      }
    }

    if (deleted) {
      return ResponseEntity.noContent().build();
    } else {
      log.warn("Failed to delete monitor: {} - not found in any accessible tenant", id);
      return ResponseEntity.notFound().build();
    }
  }

  @PatchMapping("/{id}/state")
  public ResponseEntity<MonitorResponseDto> updateMonitorState(
      @PathVariable Integer id, @RequestParam MonitorState state) {
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.info("Updating state for monitor ID: {} to {} for tenants: {}", id, state, tenantIds);

    for (Integer tenantId : tenantIds) {
      try {
        Monitor monitor = monitorService.updateMonitorState(tenantId, id, state);
        MonitorResponseDto responseDto = entityMapper.toDto(monitor);
        return ResponseEntity.ok(responseDto);
      } catch (IllegalArgumentException e) {
        log.debug("Monitor {} not found in tenant {}", id, tenantId);
      }
    }

    log.warn("Failed to update monitor state: {} - not found in any accessible tenant", id);
    return ResponseEntity.notFound().build();
  }

  // Legacy endpoint for backward compatibility
  @PatchMapping("/{id}/toggle-status")
  public ResponseEntity<MonitorResponseDto> toggleMonitorStatus(@PathVariable Integer id) {
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.info("Toggling status for monitor ID: {} for tenants: {}", id, tenantIds);

    for (Integer tenantId : tenantIds) {
      try {
        Monitor monitor = monitorService.getMonitorById(tenantId, id);
        MonitorState newState =
            (monitor.getState() == MonitorState.ACTIVE)
                ? MonitorState.INACTIVE
                : MonitorState.ACTIVE;
        Monitor updatedMonitor = monitorService.updateMonitorState(tenantId, id, newState);
        MonitorResponseDto responseDto = entityMapper.toDto(updatedMonitor);
        return ResponseEntity.ok(responseDto);
      } catch (IllegalArgumentException e) {
        log.debug("Monitor {} not found in tenant {}", id, tenantId);
      }
    }

    log.warn("Failed to toggle monitor status: {} - not found in any accessible tenant", id);
    return ResponseEntity.notFound().build();
  }
}
