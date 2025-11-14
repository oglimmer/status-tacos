/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import de.oglimmer.status_tacos.util.TenantCodeGenerator;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenantManagementService {

  private final TenantService tenantService;
  private final UserService userService;
  private final UserRepository userRepository;
  private final TenantCodeGenerator tenantCodeGenerator;
  private final AlertContactRepository alertContactRepository;

  public Tenant createTenantAndAssignUser(String name, String code, String description, User user) {
    log.info("Creating new tenant with code: {} and assigning user {}", code, user.getEmail());

    // Create the tenant (without user assignment)
    Tenant tenant = tenantService.createTenant(name, code, description);

    // Assign user to the new tenant
    if (user.getTenants() == null) {
      user.setTenants(new HashSet<>());
    }
    user.getTenants().add(tenant);
    userRepository.save(user);

    log.info(
        "Successfully created tenant {} and assigned user {}", tenant.getCode(), user.getEmail());
    return tenant;
  }

  public Tenant createTenantAndAssignCurrentUser(String name, String code, String description) {
    log.info("Creating new tenant with code: {} and assigning current user", code);

    // Get current user (this won't create a user, just get existing)
    User currentUser = userService.getCurrentUser();
    if (currentUser == null) {
      throw new IllegalStateException("No authenticated user found");
    }

    return createTenantAndAssignUser(name, code, description, currentUser);
  }

  public User createUserWithTenant(Jwt jwt, String subject) {
    log.info("Creating new user with auto-created tenant for subject: {}", subject);

    String email = jwt.getClaimAsString("email");
    String firstName = jwt.getClaimAsString("given_name");
    String lastName = jwt.getClaimAsString("family_name");
    String name = jwt.getClaimAsString("name");

    if (firstName == null && lastName == null && name != null) {
      String[] nameParts = name.split(" ", 2);
      firstName = nameParts[0];
      if (nameParts.length > 1) {
        lastName = nameParts[1];
      }
    }

    // Create user first (without tenant)
    User newUser =
        User.builder()
            .email(email != null ? email : subject + "@unknown.com")
            .oidcSubject(subject)
            .firstName(firstName)
            .lastName(lastName)
            .tenants(new HashSet<>())
            .isActive(true)
            .build();

    User savedUser = userRepository.save(newUser);

    // Create a new tenant for the new user
    String tenantName = firstName + " " + lastName;
    if (tenantName.trim().isEmpty()) {
      tenantName = email != null ? email.split("@")[0] : "User";
    }
    String tenantCode = tenantCodeGenerator.generateUniqueTenantCode(tenantName);

    Tenant newTenant =
        tenantService.createTenant(
            tenantName + "'s Workspace", tenantCode, "Auto-created tenant for user " + subject);

    // Assign user to tenant
    savedUser.getTenants().add(newTenant);
    User finalUser = userRepository.save(savedUser);

    // Create default email alert contact for the new user
    if (email != null && !email.endsWith("@unknown.com")) {
      try {
        AlertContact defaultAlertContact =
            AlertContact.builder()
                .tenant(newTenant)
                .tenantId(newTenant.getId())
                .type(AlertContact.AlertContactType.EMAIL)
                .value(email)
                .name("Default Email Alert")
                .isActive(true)
                .build();

        alertContactRepository.save(defaultAlertContact);
        log.info("Created default email alert contact for user: {}", email);
      } catch (Exception e) {
        log.warn("Failed to create default alert contact for user {}: {}", email, e.getMessage());
      }
    }

    // Force initialization of lazy-loaded tenants collection
    finalUser.getTenants().size();

    return finalUser;
  }
}
