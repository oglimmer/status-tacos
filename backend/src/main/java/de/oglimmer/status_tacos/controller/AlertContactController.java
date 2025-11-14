/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.AlertContactRequestDto;
import de.oglimmer.status_tacos.dto.AlertContactResponseDto;
import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.service.AlertContactService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/alert-contacts")
@RequiredArgsConstructor
public class AlertContactController {

  private final AlertContactService alertContactService;
  private final UserTenantResolver userTenantResolver;

  @PostMapping
  public ResponseEntity<AlertContactResponseDto> createAlertContact(
      @RequestBody AlertContactRequestDto request,
      @RequestParam Integer tenantId,
      Authentication authentication) {

    validateAlertContactRequest(request);

    String username = authentication.getName();
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();

    AlertContactResponseDto response =
        alertContactService.createAlertContact(request, tenantId, tenantIds);
    log.info(
        "User {} created alert contact {} for tenant {}", username, response.getId(), tenantId);

    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<List<AlertContactResponseDto>> getAllAlertContacts(
      @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
      Authentication authentication) {

    String username = authentication.getName();
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();

    List<AlertContactResponseDto> contacts =
        activeOnly
            ? alertContactService.getActiveAlertContacts(tenantIds)
            : alertContactService.getAllAlertContacts(tenantIds);

    log.debug(
        "User {} retrieved {} alert contacts (activeOnly: {})",
        username,
        contacts.size(),
        activeOnly);
    return ResponseEntity.ok(contacts);
  }

  @GetMapping("/tenant/{tenantId}")
  public ResponseEntity<List<AlertContactResponseDto>> getAlertContactsByTenant(
      @PathVariable Integer tenantId, Authentication authentication) {

    String username = authentication.getName();
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();

    List<AlertContactResponseDto> contacts =
        alertContactService.getAlertContactsByTenant(tenantId, tenantIds);
    log.debug(
        "User {} retrieved {} alert contacts for tenant {}", username, contacts.size(), tenantId);

    return ResponseEntity.ok(contacts);
  }

  @GetMapping("/{id}")
  public ResponseEntity<AlertContactResponseDto> getAlertContactById(
      @PathVariable Integer id, Authentication authentication) {

    String username = authentication.getName();
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();

    AlertContactResponseDto contact = alertContactService.getAlertContactById(id, tenantIds);
    log.debug("User {} retrieved alert contact {}", username, id);

    return ResponseEntity.ok(contact);
  }

  @PutMapping("/{id}")
  public ResponseEntity<AlertContactResponseDto> updateAlertContact(
      @PathVariable Integer id,
      @RequestBody AlertContactRequestDto request,
      Authentication authentication) {

    validateAlertContactRequest(request);

    String username = authentication.getName();
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();

    AlertContactResponseDto response =
        alertContactService.updateAlertContact(id, request, tenantIds);
    log.info("User {} updated alert contact {}", username, id);

    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteAlertContact(
      @PathVariable Integer id, Authentication authentication) {

    String username = authentication.getName();
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();

    alertContactService.deleteAlertContact(id, tenantIds);
    log.info("User {} deleted alert contact {}", username, id);

    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/toggle-status")
  public ResponseEntity<AlertContactResponseDto> toggleAlertContactStatus(
      @PathVariable Integer id, Authentication authentication) {

    String username = authentication.getName();
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();

    AlertContactResponseDto response = alertContactService.toggleAlertContactStatus(id, tenantIds);
    log.info("User {} toggled status of alert contact {} to {}", username, id, response.isActive());

    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/test")
  public ResponseEntity<Void> sendTestNotification(
      @PathVariable Integer id, Authentication authentication) {

    String username = authentication.getName();
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();

    alertContactService.sendTestNotification(id, tenantIds);
    log.info("User {} sent test notification to alert contact {}", username, id);

    return ResponseEntity.ok().build();
  }

  private void validateAlertContactRequest(AlertContactRequestDto request) {
    if (request.getType() == AlertContact.AlertContactType.EMAIL) {
      if (!request.isValidEmail()) {
        throw new IllegalArgumentException("Invalid email format for EMAIL type contact");
      }
    } else if (request.getType() == AlertContact.AlertContactType.HTTP) {
      if (!request.isValidUrl()) {
        throw new IllegalArgumentException("Invalid URL format for HTTP type contact");
      }
      if (request.getHttpMethod() != null && !request.getHttpMethod().matches("(?i)^(GET|POST)$")) {
        throw new IllegalArgumentException("HTTP method must be GET or POST");
      }
    }
  }
}
