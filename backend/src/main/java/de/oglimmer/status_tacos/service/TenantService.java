/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenantService {

  /** The code is the path of the public status page: /{code}/status. */
  private static final Pattern CODE_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,50}");

  /** A code that is not valid or that another tenant has. The message is for the user. */
  public static class InvalidTenantCodeException extends RuntimeException {
    public InvalidTenantCodeException(String message) {
      super(message);
    }
  }

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;

  public Tenant createTenant(String name, String code, String description) {
    log.info("Creating new tenant with code: {}", code);

    checkCodeFormat(code);
    if (tenantRepository.existsByCode(code)) {
      throw codeTaken(code);
    }

    Tenant tenant =
        Tenant.builder().name(name).code(code).description(description).isActive(true).build();

    return tenantRepository.save(tenant);
  }

  @Transactional(readOnly = true)
  public Optional<Tenant> getTenantById(Integer id) {
    log.debug("Getting tenant by ID: {}", id);
    return tenantRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<Tenant> getTenantByCode(String code) {
    log.debug("Getting tenant by code: {}", code);
    return tenantRepository.findByCode(code);
  }

  @Transactional(readOnly = true)
  public List<Tenant> getAllActiveTenants() {
    log.debug("Getting all active tenants");
    return tenantRepository.findAllActive();
  }

  /**
   * @param code the new code, or null to keep it. A new code moves the public status page to the
   *     new URL. The old URL then gives 404.
   */
  public Tenant updateTenant(Integer id, String name, String code, String description) {
    log.info("Updating tenant ID: {}", id);

    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + id));

    if (code != null && !code.equals(tenant.getCode())) {
      checkCodeFormat(code);
      // The column can compare without case, so look for another tenant, not for the code
      if (tenantRepository.findByCode(code).filter(t -> !t.getId().equals(id)).isPresent()) {
        throw codeTaken(code);
      }
      log.info("Changing code of tenant ID {} from {} to {}", id, tenant.getCode(), code);
      tenant.setCode(code);
    }
    tenant.setName(name);
    tenant.setDescription(description);

    try {
      // Flush here, so a parallel change to the same code fails with a clear message
      return tenantRepository.saveAndFlush(tenant);
    } catch (DataIntegrityViolationException e) {
      throw codeTaken(code);
    }
  }

  private static void checkCodeFormat(String code) {
    if (code == null || !CODE_PATTERN.matcher(code).matches()) {
      throw new InvalidTenantCodeException(
          "The code must have 1 to 50 letters, numbers, hyphens or underscores.");
    }
  }

  private static InvalidTenantCodeException codeTaken(String code) {
    return new InvalidTenantCodeException("Another tenant already has the code '" + code + "'.");
  }

  public Tenant toggleTenantStatus(Integer id) {
    log.info("Toggling status for tenant ID: {}", id);

    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + id));

    tenant.setIsActive(!tenant.getIsActive());

    return tenantRepository.save(tenant);
  }

  @Transactional(readOnly = true)
  public boolean tenantExists(String code) {
    return tenantRepository.existsByCode(code);
  }

  @Transactional(readOnly = true)
  public List<Tenant> getUserTenants(Set<Integer> tenantIds) {
    log.debug("Getting tenants for user tenant IDs: {}", tenantIds);
    return tenantRepository.findAllById(tenantIds);
  }

  @Transactional(readOnly = true)
  public List<Tenant> getUserActiveTenants(Set<Integer> tenantIds) {
    log.debug("Getting active tenants for user tenant IDs: {}", tenantIds);
    return tenantRepository.findAllById(tenantIds).stream().filter(Tenant::getIsActive).toList();
  }

  @Transactional(readOnly = true)
  public List<User> getTenantUsers(Integer tenantId) {
    log.debug("Getting users for tenant ID: {}", tenantId);
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(
                () -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));

    return new ArrayList<>(tenant.getUsers());
  }

  public void assignUserToTenant(Integer tenantId, String email) {
    log.info("Assigning user with email {} to tenant ID: {}", email, tenantId);

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(
                () -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));

    User user =
        userRepository
            .findByEmailAndIsActiveTrue(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

    if (user.getTenants().contains(tenant)) {
      throw new IllegalArgumentException("User is already assigned to this tenant");
    }

    user.getTenants().add(tenant);
    userRepository.save(user);
  }

  public void removeUserFromTenant(Integer tenantId, String email) {
    log.info("Removing user with email {} from tenant ID: {}", email, tenantId);

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(
                () -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));

    User user =
        userRepository
            .findByEmailAndIsActiveTrue(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

    if (!user.getTenants().contains(tenant)) {
      throw new IllegalArgumentException("User is not assigned to this tenant");
    }

    user.getTenants().remove(tenant);
    userRepository.save(user);
  }
}
