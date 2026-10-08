/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.dto.AlertContactRequestDto;
import de.oglimmer.status_tacos.dto.AlertContactResponseDto;
import de.oglimmer.status_tacos.dto.PushAlertRequestDto;
import de.oglimmer.status_tacos.dto.TenantResponseDto;
import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertContactService {

  private final AlertContactRepository alertContactRepository;
  private final TenantRepository tenantRepository;
  private final MonitorRepository monitorRepository;
  private final AlertService alertService;

  @Transactional
  public AlertContactResponseDto createAlertContact(
      AlertContactRequestDto request, Integer tenantId, Set<Integer> allowedTenantIds) {
    validateTenantAccess(tenantId, allowedTenantIds);
    rejectIosPush(request.getType());

    // Check for duplicate contact
    if (alertContactRepository.existsByTenantIdAndValueAndType(
        tenantId, request.getValue(), request.getType())) {
      throw new IllegalArgumentException(
          "Alert contact with this value already exists for the tenant");
    }

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

    AlertContact alertContact =
        AlertContact.builder()
            .tenant(tenant)
            .tenantId(tenantId)
            .type(request.getType())
            .value(request.getValue())
            .name(request.getName())
            .isActive(request.isActive())
            .httpMethod(request.getHttpMethod())
            .httpBody(request.getHttpBody())
            .httpContentType(request.getHttpContentType())
            .build();

    if (request.getHttpHeaders() != null) {
      alertContact.setHttpHeadersFromMap(request.getHttpHeaders());
    }

    applyMonitorScope(alertContact, request, tenantId);
    alertContact.validateValue();
    AlertContact saved = alertContactRepository.save(alertContact);

    log.info("Created alert contact {} for tenant {}", saved.getId(), tenantId);
    return convertToDto(saved);
  }

  @Transactional(readOnly = true)
  public List<AlertContactResponseDto> getAllAlertContacts(Set<Integer> tenantIds) {
    return alertContactRepository.findByTenantIdIn(tenantIds).stream()
        .map(this::convertToDto)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<AlertContactResponseDto> getActiveAlertContacts(Set<Integer> tenantIds) {
    return alertContactRepository.findByTenantIdInAndIsActiveTrue(tenantIds).stream()
        .map(this::convertToDto)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<AlertContactResponseDto> getAlertContactsByTenant(
      Integer tenantId, Set<Integer> allowedTenantIds) {
    validateTenantAccess(tenantId, allowedTenantIds);

    return alertContactRepository.findByTenantId(tenantId).stream()
        .map(this::convertToDto)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public AlertContactResponseDto getAlertContactById(Integer id, Set<Integer> tenantIds) {
    AlertContact alertContact =
        alertContactRepository
            .findByIdAndTenantIdIn(id, tenantIds)
            .orElseThrow(
                () -> new IllegalArgumentException("Alert contact not found or access denied"));

    return convertToDto(alertContact);
  }

  @Transactional
  public AlertContactResponseDto updateAlertContact(
      Integer id, AlertContactRequestDto request, Set<Integer> tenantIds) {
    AlertContact alertContact =
        alertContactRepository
            .findByIdAndTenantIdIn(id, tenantIds)
            .orElseThrow(
                () -> new IllegalArgumentException("Alert contact not found or access denied"));

    rejectIosPush(alertContact.getType());
    rejectIosPush(request.getType());

    // Check for duplicate contact (excluding current one)
    if (alertContactRepository.existsByTenantIdAndValueAndTypeAndIdNot(
        alertContact.getTenantId(), request.getValue(), request.getType(), id)) {
      throw new IllegalArgumentException(
          "Alert contact with this value already exists for the tenant");
    }

    alertContact.setType(request.getType());
    alertContact.setValue(request.getValue());
    alertContact.setName(request.getName());
    alertContact.setActive(request.isActive());
    alertContact.setHttpMethod(request.getHttpMethod());
    alertContact.setHttpBody(request.getHttpBody());
    alertContact.setHttpContentType(request.getHttpContentType());

    if (request.getHttpHeaders() != null) {
      alertContact.setHttpHeadersFromMap(request.getHttpHeaders());
    } else {
      alertContact.setHttpHeaders(null);
    }

    applyMonitorScope(alertContact, request, alertContact.getTenantId());
    alertContact.validateValue();
    AlertContact saved = alertContactRepository.save(alertContact);

    log.info("Updated alert contact {} for tenant {}", saved.getId(), saved.getTenantId());
    return convertToDto(saved);
  }

  @Transactional
  public void deleteAlertContact(Integer id, Set<Integer> tenantIds) {
    AlertContact alertContact =
        alertContactRepository
            .findByIdAndTenantIdIn(id, tenantIds)
            .orElseThrow(
                () -> new IllegalArgumentException("Alert contact not found or access denied"));

    rejectIosPush(alertContact.getType());
    alertContactRepository.delete(alertContact);
    log.info("Deleted alert contact {} for tenant {}", id, alertContact.getTenantId());
  }

  @Transactional
  public AlertContactResponseDto toggleAlertContactStatus(Integer id, Set<Integer> tenantIds) {
    AlertContact alertContact =
        alertContactRepository
            .findByIdAndTenantIdIn(id, tenantIds)
            .orElseThrow(
                () -> new IllegalArgumentException("Alert contact not found or access denied"));

    rejectIosPush(alertContact.getType());
    alertContact.setActive(!alertContact.isActive());
    AlertContact saved = alertContactRepository.save(alertContact);

    log.info(
        "Toggled alert contact {} status to {} for tenant {}",
        saved.getId(),
        saved.isActive(),
        saved.getTenantId());
    return convertToDto(saved);
  }

  @Transactional
  public void sendTestNotification(Integer id, Set<Integer> tenantIds) {
    AlertContact alertContact =
        alertContactRepository
            .findByIdAndTenantIdIn(id, tenantIds)
            .orElseThrow(
                () -> new IllegalArgumentException("Alert contact not found or access denied"));

    rejectIosPush(alertContact.getType());
    if (!alertContact.isActive()) {
      throw new IllegalArgumentException("Cannot send test notification to inactive alert contact");
    }

    // Send a test notification using the alert service
    alertService.sendTestNotification(alertContact);

    log.info(
        "Sent test notification to alert contact {} for tenant {}",
        alertContact.getId(),
        alertContact.getTenantId());
  }

  /**
   * Sets which monitors the contact is alerted for. Selected monitors must belong to the tenant of
   * the contact, so a contact never gets alerts of another tenant.
   */
  private void applyMonitorScope(
      AlertContact alertContact, AlertContactRequestDto request, Integer tenantId) {
    alertContact.setAllMonitors(request.isAllMonitors());
    if (request.isAllMonitors()) {
      alertContact.getMonitors().clear();
      return;
    }

    Set<Integer> monitorIds = request.getMonitorIds() == null ? Set.of() : request.getMonitorIds();
    if (monitorIds.isEmpty()) {
      throw new IllegalArgumentException(
          "Select at least one monitor or send alerts for all monitors");
    }

    List<Monitor> monitors = monitorRepository.findAllById(monitorIds);
    boolean allInTenant =
        monitors.size() == monitorIds.size()
            && monitors.stream().allMatch(monitor -> tenantId.equals(monitor.getTenantId()));
    if (!allInTenant) {
      throw new IllegalArgumentException("All selected monitors must belong to the tenant");
    }

    alertContact.getMonitors().clear();
    alertContact.getMonitors().addAll(new HashSet<>(monitors));
  }

  private void validateTenantAccess(Integer tenantId, Set<Integer> allowedTenantIds) {
    if (!allowedTenantIds.contains(tenantId)) {
      throw new IllegalArgumentException("Access denied to tenant");
    }
  }

  private AlertContactResponseDto convertToDto(AlertContact alertContact) {
    return AlertContactResponseDto.builder()
        .id(alertContact.getId())
        .type(alertContact.getType())
        .value(alertContact.getValue())
        .name(alertContact.getName())
        .isActive(alertContact.isActive())
        .tenant(convertTenantToDto(alertContact.getTenant()))
        .createdAt(alertContact.getCreatedAt())
        .updatedAt(alertContact.getUpdatedAt())
        .httpMethod(alertContact.getHttpMethod())
        .httpHeaders(alertContact.getHttpHeadersMap())
        .httpBody(alertContact.getHttpBody())
        .httpContentType(alertContact.getHttpContentType())
        .allMonitors(alertContact.isAllMonitors())
        .owner(ownerReference(alertContact.getOwner()))
        .monitors(
            alertContact.getMonitors().stream()
                .sorted(Comparator.comparing(Monitor::getName, String.CASE_INSENSITIVE_ORDER))
                .map(
                    monitor ->
                        new AlertContactResponseDto.MonitorReference(
                            monitor.getId(), monitor.getName()))
                .toList())
        .build();
  }

  private AlertContactResponseDto.OwnerReference ownerReference(User owner) {
    if (owner == null) {
      return null;
    }
    String name =
        Stream.of(owner.getFirstName(), owner.getLastName())
            .filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining(" "));
    return new AlertContactResponseDto.OwnerReference(
        owner.getId(), owner.getEmail(), name.isEmpty() ? null : name);
  }

  /** iOS push contacts belong to one user and are managed only in the iOS app. */
  private static void rejectIosPush(AlertContact.AlertContactType type) {
    if (type == AlertContact.AlertContactType.IOS_PUSH) {
      throw new IllegalArgumentException("iOS push alerts are managed in the iOS app");
    }
  }

  // iOS push alerts of the current user, managed by the iOS app

  @Transactional(readOnly = true)
  public List<AlertContactResponseDto> getIosPushContacts(User owner, Set<Integer> tenantIds) {
    return alertContactRepository
        .findByTypeAndOwnerIdAndTenantIdIn(
            AlertContact.AlertContactType.IOS_PUSH, owner.getId(), tenantIds)
        .stream()
        .map(this::convertToDto)
        .toList();
  }

  /** Creates or changes the push alert of the user for the tenant. */
  @Transactional
  public AlertContactResponseDto saveIosPushContact(
      User owner, Integer tenantId, PushAlertRequestDto request, Set<Integer> allowedTenantIds) {
    validateTenantAccess(tenantId, allowedTenantIds);

    AlertContact contact =
        alertContactRepository
            .findByTenantIdAndTypeAndOwnerId(
                tenantId, AlertContact.AlertContactType.IOS_PUSH, owner.getId())
            .orElseGet(() -> newIosPushContact(owner, tenantId));
    contact.setActive(request.isActive());

    AlertContactRequestDto scope = new AlertContactRequestDto();
    scope.setAllMonitors(request.isAllMonitors());
    scope.setMonitorIds(request.getMonitorIds());
    applyMonitorScope(contact, scope, tenantId);

    contact.validateValue();
    AlertContact saved = alertContactRepository.save(contact);
    log.info(
        "Saved iOS push alert {} of user {} for tenant {}", saved.getId(), owner.getId(), tenantId);
    return convertToDto(saved);
  }

  /**
   * @return false if the user has no push alert for the tenant
   */
  @Transactional
  public boolean deleteIosPushContact(User owner, Integer tenantId, Set<Integer> allowedTenantIds) {
    validateTenantAccess(tenantId, allowedTenantIds);
    return alertContactRepository
        .findByTenantIdAndTypeAndOwnerId(
            tenantId, AlertContact.AlertContactType.IOS_PUSH, owner.getId())
        .map(
            contact -> {
              alertContactRepository.delete(contact);
              log.info("Deleted iOS push alert {} of user {}", contact.getId(), owner.getId());
              return true;
            })
        .orElse(false);
  }

  /**
   * Sends a test notification to the devices of the user.
   *
   * @return false if the user has no push alert for the tenant
   */
  @Transactional
  public boolean sendIosPushTest(User owner, Integer tenantId, Set<Integer> allowedTenantIds) {
    validateTenantAccess(tenantId, allowedTenantIds);
    Optional<AlertContact> contact =
        alertContactRepository.findByTenantIdAndTypeAndOwnerId(
            tenantId, AlertContact.AlertContactType.IOS_PUSH, owner.getId());
    contact.ifPresent(alertService::sendTestNotification);
    return contact.isPresent();
  }

  private AlertContact newIosPushContact(User owner, Integer tenantId) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    return AlertContact.builder()
        .tenant(tenant)
        .tenantId(tenantId)
        .type(AlertContact.AlertContactType.IOS_PUSH)
        .value(AlertContact.iosPushValue(owner))
        .name("iOS push: " + owner.getEmail())
        .owner(owner)
        .build();
  }

  private TenantResponseDto convertTenantToDto(Tenant tenant) {
    return TenantResponseDto.builder()
        .id(tenant.getId())
        .name(tenant.getName())
        .code(tenant.getCode())
        .description(tenant.getDescription())
        .isActive(tenant.getIsActive())
        .createdAt(tenant.getCreatedAt())
        .updatedAt(tenant.getUpdatedAt())
        .build();
  }
}
