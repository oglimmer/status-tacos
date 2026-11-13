/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import de.oglimmer.status_tacos.dto.MonitorRequestDto;
import de.oglimmer.status_tacos.dto.MonitorResponseDto;
import de.oglimmer.status_tacos.mapper.EntityMapper;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.SilenceDuration;
import de.oglimmer.status_tacos.service.MonitorLimitService;
import de.oglimmer.status_tacos.service.MonitorMoveService;
import de.oglimmer.status_tacos.service.MonitorService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/monitors")
@RequiredArgsConstructor
@Slf4j
public class MonitorController {

  private final MonitorService monitorService;
  private final MonitorMoveService monitorMoveService;
  private final MonitorLimitService monitorLimitService;
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

    userTenantResolver
        .getCurrentUser()
        .ifPresent(
            user ->
                monitorLimitService.checkCanCreateMonitor(
                    user, userTenantResolver.getCurrentUserTenantIds(), requestDto.getTenantId()));

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

  /**
   * Moves the monitor with all its history to another tenant. The user needs access to both
   * tenants. Alert contacts that are limited to selected monitors lose this monitor.
   */
  @PatchMapping("/{id}/tenant")
  public ResponseEntity<MonitorResponseDto> moveMonitorToTenant(
      @PathVariable Integer id, @RequestParam Integer tenantId) {
    if (!userTenantResolver.hasAccessToTenant(tenantId)) {
      log.warn("User attempted to move monitor {} to unauthorized tenant: {}", id, tenantId);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    try {
      Monitor monitor = monitorMoveService.moveMonitorToTenant(tenantIds, id, tenantId);
      return ResponseEntity.ok(entityMapper.toDto(monitor));
    } catch (IllegalArgumentException e) {
      log.warn("Failed to move monitor: {} - not found in any accessible tenant", id);
      return ResponseEntity.notFound().build();
    } catch (IllegalStateException e) {
      log.warn("Failed to move monitor {}: {}", id, e.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
  }

  @PatchMapping("/{id}/state")
  public ResponseEntity<MonitorResponseDto> updateMonitorState(
      @PathVariable Integer id,
      @RequestParam MonitorState state,
      @RequestParam(required = false) String silentFor) {
    SilenceDuration silence;
    try {
      silence = SilenceDuration.fromCode(silentFor);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.info("Updating state for monitor ID: {} to {} for tenants: {}", id, state, tenantIds);

    for (Integer tenantId : tenantIds) {
      try {
        Monitor monitor = monitorService.updateMonitorState(tenantId, id, state, silence);
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

  /**
   * The JSON body cannot be read, for example an unknown environment or state. 400 with a message
   * that names the field and the allowed values.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, String>> unreadableRequest(HttpMessageNotReadableException e) {
    String message = describe(e);
    log.warn("Invalid monitor request: {}", message);
    return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", message));
  }

  static String describe(HttpMessageNotReadableException e) {
    Throwable cause = e.getCause();
    if (cause instanceof ValueInstantiationException vie
        && vie.getCause() instanceof IllegalArgumentException iae) {
      // Our own JSON creators, for example MonitorEnvironment: their message is already clear
      return iae.getMessage();
    }
    if (cause instanceof MismatchedInputException mie
        && mie.getTargetType() != null
        && mie.getTargetType().isEnum()) {
      String allowed =
          Arrays.stream(mie.getTargetType().getEnumConstants())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      String value = mie instanceof InvalidFormatException ife ? ", not: " + ife.getValue() : "";
      return fieldOf(mie) + " must be one of " + allowed + value;
    }
    if (cause instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
      return "Invalid value for " + fieldOf(jme);
    }
    return "The request body is not valid JSON";
  }

  private static String fieldOf(JsonMappingException e) {
    return e.getPath().isEmpty() ? "value" : String.valueOf(e.getPath().getLast().getFieldName());
  }

  /** The user has the maximum number of monitors. The frontend shows the message. */
  @ExceptionHandler(MonitorLimitService.MonitorLimitReachedException.class)
  public ResponseEntity<Map<String, String>> limitReached(
      MonitorLimitService.MonitorLimitReachedException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "monitor_limit_reached", "message", e.getMessage()));
  }
}
