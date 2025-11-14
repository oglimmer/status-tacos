/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.TenantRequestDto;
import de.oglimmer.status_tacos.dto.TenantResponseDto;
import de.oglimmer.status_tacos.dto.TenantUpdateRequestDto;
import de.oglimmer.status_tacos.dto.UserResponseDto;
import de.oglimmer.status_tacos.mapper.EntityMapper;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.service.TenantManagementService;
import de.oglimmer.status_tacos.service.TenantService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/tenants")
@RequiredArgsConstructor
@Slf4j
public class TenantController {

  private final TenantService tenantService;
  private final TenantManagementService tenantManagementService;
  private final UserTenantResolver userTenantResolver;
  private final EntityMapper entityMapper;

  @PostMapping
  public ResponseEntity<TenantResponseDto> createTenant(
      @Valid @RequestBody TenantRequestDto requestDto) {
    log.info("Creating tenant: {}", requestDto.getName());

    try {
      Tenant tenant =
          tenantManagementService.createTenantAndAssignCurrentUser(
              requestDto.getName(), requestDto.getCode(), requestDto.getDescription());
      return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(tenant));
    } catch (IllegalArgumentException e) {
      log.warn("Failed to create tenant: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<TenantResponseDto> getTenant(@PathVariable Integer id) {
    if (!userTenantResolver.hasAccessToTenant(id)) {
      log.warn("User attempted to access unauthorized tenant: {}", id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    log.debug("Getting tenant by ID: {}", id);

    return tenantService
        .getTenantById(id)
        .map(this::convertToDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/code/{code}")
  public ResponseEntity<TenantResponseDto> getTenantByCode(@PathVariable String code) {
    log.debug("Getting tenant by code: {}", code);

    return tenantService
        .getTenantByCode(code)
        .map(this::convertToDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  public ResponseEntity<List<TenantResponseDto>> getAllTenants(
      @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {

    Set<Integer> userTenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug("Getting tenants - activeOnly: {} for user tenants: {}", activeOnly, userTenantIds);

    List<Tenant> tenants =
        activeOnly
            ? tenantService.getUserActiveTenants(userTenantIds)
            : tenantService.getUserTenants(userTenantIds);

    List<TenantResponseDto> dtos =
        tenants.stream().map(this::convertToDto).collect(Collectors.toList());

    return ResponseEntity.ok(dtos);
  }

  @PutMapping("/{id}")
  public ResponseEntity<TenantResponseDto> updateTenant(
      @PathVariable Integer id, @Valid @RequestBody TenantUpdateRequestDto requestDto) {

    if (!userTenantResolver.hasAccessToTenant(id)) {
      log.warn("User attempted to update unauthorized tenant: {}", id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    log.info("Updating tenant ID: {}", id);

    try {
      Tenant tenant =
          tenantService.updateTenant(id, requestDto.getName(), requestDto.getDescription());
      return ResponseEntity.ok(convertToDto(tenant));
    } catch (IllegalArgumentException e) {
      log.warn("Failed to update tenant: {}", e.getMessage());
      return ResponseEntity.notFound().build();
    }
  }

  @PatchMapping("/{id}/toggle-status")
  public ResponseEntity<TenantResponseDto> toggleTenantStatus(@PathVariable Integer id) {
    if (!userTenantResolver.hasAccessToTenant(id)) {
      log.warn("User attempted to toggle status for unauthorized tenant: {}", id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    log.info("Toggling status for tenant ID: {}", id);

    try {
      Tenant tenant = tenantService.toggleTenantStatus(id);
      return ResponseEntity.ok(convertToDto(tenant));
    } catch (IllegalArgumentException e) {
      log.warn("Failed to toggle tenant status: {}", e.getMessage());
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/{id}/users")
  public ResponseEntity<List<UserResponseDto>> getTenantUsers(@PathVariable Integer id) {
    if (!userTenantResolver.hasAccessToTenant(id)) {
      log.warn("User attempted to access users for unauthorized tenant: {}", id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    log.debug("Getting users for tenant ID: {}", id);

    try {
      List<User> users = tenantService.getTenantUsers(id);
      List<UserResponseDto> userDtos =
          users.stream().map(entityMapper::toDto).collect(Collectors.toList());
      return ResponseEntity.ok(userDtos);
    } catch (IllegalArgumentException e) {
      log.warn("Failed to get tenant users: {}", e.getMessage());
      return ResponseEntity.notFound().build();
    }
  }

  @PostMapping("/{id}/users")
  public ResponseEntity<Void> assignUserToTenant(
      @PathVariable Integer id, @RequestParam String email) {

    if (!userTenantResolver.hasAccessToTenant(id)) {
      log.warn("User attempted to assign user to unauthorized tenant: {}", id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    log.info("Assigning user with email {} to tenant ID: {}", email, id);

    try {
      tenantService.assignUserToTenant(id, email);
      return ResponseEntity.ok().build();
    } catch (IllegalArgumentException e) {
      log.warn("Failed to assign user to tenant: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    }
  }

  @DeleteMapping("/{id}/users")
  public ResponseEntity<Void> removeUserFromTenant(
      @PathVariable Integer id, @RequestParam String email) {

    if (!userTenantResolver.hasAccessToTenant(id)) {
      log.warn("User attempted to remove user from unauthorized tenant: {}", id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    log.info("Removing user with email {} from tenant ID: {}", email, id);

    try {
      tenantService.removeUserFromTenant(id, email);
      return ResponseEntity.ok().build();
    } catch (IllegalArgumentException e) {
      log.warn("Failed to remove user from tenant: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    }
  }

  private TenantResponseDto convertToDto(Tenant tenant) {
    return entityMapper.toDto(tenant);
  }
}
