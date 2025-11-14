/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataMigrationService implements CommandLineRunner {

  private final AlertContactRepository alertContactRepository;
  private final TenantRepository tenantRepository;

  @Override
  public void run(String... args) {
    log.info("Running data migration checks...");
    ensureDefaultAlertContactsExist();
  }

  @Transactional
  public void ensureDefaultAlertContactsExist() {
    // This method can be used to ensure each tenant has at least one alert contact
    // for future use cases or to create default contacts from user emails

    log.debug("Checking for tenants without alert contacts...");

    Iterable<Tenant> allTenants = tenantRepository.findAll();

    for (Tenant tenant : allTenants) {
      if (tenant.getIsActive()) {
        long contactCount = alertContactRepository.findByTenantId(tenant.getId()).size();

        if (contactCount == 0) {
          log.warn(
              "Tenant {} ({}) has no alert contacts configured. "
                  + "Consider adding alert contacts for this tenant.",
              tenant.getName(),
              tenant.getId());
        }
      }
    }
  }

  @Transactional
  public AlertContact createDefaultAlertContactForTenant(
      Integer tenantId, String email, String name) {
    Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
    if (tenantOpt.isEmpty()) {
      throw new IllegalArgumentException("Tenant not found: " + tenantId);
    }

    Tenant tenant = tenantOpt.get();

    // Check if this email already exists for this tenant
    boolean exists =
        alertContactRepository.existsByTenantIdAndValueAndType(
            tenantId, email, AlertContact.AlertContactType.EMAIL);

    if (exists) {
      log.debug("Alert contact with email {} already exists for tenant {}", email, tenantId);
      return null;
    }

    AlertContact alertContact =
        AlertContact.builder()
            .tenant(tenant)
            .tenantId(tenantId)
            .type(AlertContact.AlertContactType.EMAIL)
            .value(email)
            .name(name != null ? name : "Default Contact")
            .isActive(true)
            .build();

    alertContact.validateValue();
    AlertContact saved = alertContactRepository.save(alertContact);

    log.info(
        "Created default alert contact {} for tenant {} with email {}",
        saved.getId(),
        tenantId,
        email);

    return saved;
  }
}
