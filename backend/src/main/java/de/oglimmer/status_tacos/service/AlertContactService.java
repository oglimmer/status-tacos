/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.dto.AlertContactRequestDto;
import de.oglimmer.status_tacos.dto.AlertContactResponseDto;
import de.oglimmer.status_tacos.dto.TenantResponseDto;
import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
  private final AlertService alertService;

  @Transactional
  public AlertContactResponseDto createAlertContact(
      AlertContactRequestDto request, Integer tenantId, Set<Integer> allowedTenantIds) {
    validateTenantAccess(tenantId, allowedTenantIds);

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
