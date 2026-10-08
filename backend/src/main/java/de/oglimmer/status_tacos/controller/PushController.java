/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.AlertContactResponseDto;
import de.oglimmer.status_tacos.dto.PushAlertRequestDto;
import de.oglimmer.status_tacos.dto.PushDeviceRequestDto;
import de.oglimmer.status_tacos.dto.PushStatusResponseDto;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.service.AlertContactService;
import de.oglimmer.status_tacos.service.PushNotificationService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * iOS push alerts of the current user: the devices of the iOS app and one push alert per tenant
 * (for all monitors of the tenant or for selected monitors). Used by the iOS app only.
 */
@Slf4j
@RestController
@RequestMapping("/v1/push")
@RequiredArgsConstructor
public class PushController {

  private final PushNotificationService pushNotificationService;
  private final AlertContactService alertContactService;
  private final UserTenantResolver userTenantResolver;

  @GetMapping("/status")
  public PushStatusResponseDto getStatus() {
    User user = currentUser();
    return new PushStatusResponseDto(
        pushNotificationService.isEnabled(), pushNotificationService.deviceCount(user));
  }

  /** Called by the app on every start: the token can change. */
  @PutMapping("/devices")
  public ResponseEntity<Void> registerDevice(@RequestBody PushDeviceRequestDto request) {
    User user = currentUser();
    pushNotificationService.registerDevice(
        user, request.getToken(), request.getEnvironment(), request.getDeviceName());
    log.info("User {} registered an iOS device ({})", user.getId(), request.getEnvironment());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/devices/{token}")
  public ResponseEntity<Void> unregisterDevice(@PathVariable String token) {
    User user = currentUser();
    boolean removed = pushNotificationService.unregisterDevice(user, token);
    log.info("User {} unregistered an iOS device (found: {})", user.getId(), removed);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/alerts")
  public List<AlertContactResponseDto> getAlerts() {
    return alertContactService.getIosPushContacts(currentUser(), tenantIds());
  }

  @PutMapping("/alerts/{tenantId}")
  public AlertContactResponseDto saveAlert(
      @PathVariable Integer tenantId, @RequestBody PushAlertRequestDto request) {
    Set<Integer> tenantIds = checkedTenantIds(tenantId);
    return alertContactService.saveIosPushContact(currentUser(), tenantId, request, tenantIds);
  }

  @DeleteMapping("/alerts/{tenantId}")
  public ResponseEntity<Void> deleteAlert(@PathVariable Integer tenantId) {
    Set<Integer> tenantIds = checkedTenantIds(tenantId);
    if (!alertContactService.deleteIosPushContact(currentUser(), tenantId, tenantIds)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No push alert for this tenant");
    }
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/alerts/{tenantId}/test")
  public ResponseEntity<Void> sendTest(@PathVariable Integer tenantId) {
    Set<Integer> tenantIds = checkedTenantIds(tenantId);
    if (!alertContactService.sendIosPushTest(currentUser(), tenantId, tenantIds)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No push alert for this tenant");
    }
    return ResponseEntity.ok().build();
  }

  /** Bad input, for example an invalid token or monitors of another tenant. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }

  /** The server cannot do it now, for example APNs is off or no device got the test. */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
  }

  /** Push alerts need a user record. GET /v1/users/me creates it. */
  private User currentUser() {
    return userTenantResolver
        .getCurrentUser()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown user"));
  }

  private Set<Integer> tenantIds() {
    return userTenantResolver.getCurrentUserTenantIds();
  }

  private Set<Integer> checkedTenantIds(Integer tenantId) {
    Set<Integer> tenantIds = tenantIds();
    if (!tenantIds.contains(tenantId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to this tenant");
    }
    return tenantIds;
  }
}
