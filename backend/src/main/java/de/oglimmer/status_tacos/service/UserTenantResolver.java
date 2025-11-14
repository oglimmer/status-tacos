/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTenantResolver {

  private final UserRepository userRepository;
  private final TenantService tenantService;

  public Optional<User> getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    String userIdentifier = extractUserIdentifier(authentication);
    if (userIdentifier == null) {
      return Optional.empty();
    }

    return findUserByIdentifier(userIdentifier);
  }

  private String extractUserIdentifier(Authentication authentication) {
    Object principal = authentication.getPrincipal();

    if (principal instanceof Jwt jwt) {
      return jwt.getSubject();
    }

    throw new IllegalArgumentException(
        "Unsupported authentication object: " + principal.getClass());
  }

  private Optional<User> findUserByIdentifier(String userIdentifier) {
    return userRepository.findByOidcSubjectAndIsActiveTrue(userIdentifier);
  }

  public boolean hasAccessToTenant(Integer tenantId) {
    Optional<User> currentUser = getCurrentUser();
    if (currentUser.isEmpty()) {
      return false;
    }

    Set<Tenant> userTenants = currentUser.get().getTenants();
    return userTenants != null
        && userTenants.stream().anyMatch(tenant -> tenant.getId().equals(tenantId));
  }

  public Set<Integer> getCurrentUserTenantIds() {
    Optional<User> currentUser = getCurrentUser();
    if (currentUser.isEmpty()) {
      return Set.of(1); // Default tenant for unauthenticated users
    }

    Set<Tenant> userTenants = currentUser.get().getTenants();
    if (userTenants == null || userTenants.isEmpty()) {
      return Set.of(1); // Default tenant if user has no tenants
    }

    return userTenants.stream().map(Tenant::getId).collect(java.util.stream.Collectors.toSet());
  }
}
