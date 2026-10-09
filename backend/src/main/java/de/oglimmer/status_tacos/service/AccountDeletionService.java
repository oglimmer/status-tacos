/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.AlertHistoryRepository;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import de.oglimmer.status_tacos.repository.CleanupJobRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.MonitorStatusRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a user account: the user, every tenant that only this user belongs to (with its monitors,
 * check results and alert contacts), and the login account in Keycloak. Shared tenants stay with
 * their other members. For a user who signed in with Apple, the Apple tokens are revoked first, as
 * Apple requires.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final MonitorRepository monitorRepository;
  private final CheckResultRepository checkResultRepository;
  private final MonitorStatusRepository monitorStatusRepository;
  private final AlertHistoryRepository alertHistoryRepository;
  private final CleanupJobRepository cleanupJobRepository;
  private final KeycloakAdminClient keycloakAdminClient;
  private final AppleSignInClient appleSignInClient;

  /**
   * @param userAccessToken the user's access token of this request. Keycloak hands out the stored
   *     Apple tokens only with it.
   * @return true if the login account in Keycloak was deleted too, false if Keycloak deletion is
   *     off
   * @throws IllegalStateException if Keycloak or Apple refuses. No data is deleted then; only an
   *     Apple revocation that already happened stays (the user can sign in with Apple again).
   */
  @Transactional
  public boolean deleteAccount(User user, String userAccessToken) {
    log.info("Deleting account of user {} (id={})", user.getEmail(), user.getId());

    // First: the stored Apple tokens exist only while the Keycloak user exists.
    revokeAppleSignIn(user, userAccessToken);

    List<Integer> soleTenantIds =
        user.getTenants().stream()
            .map(Tenant::getId)
            .filter(tenantId -> userRepository.countMembersOfTenant(tenantId) == 1)
            .toList();

    // Cascades to the memberships, the push devices and the iOS push contacts of the user.
    userRepository.deleteUserById(user.getId());

    for (Integer tenantId : soleTenantIds) {
      deleteTenant(tenantId);
    }

    // Last: if Keycloak fails, the exception rolls back the database deletes above.
    boolean identityDeleted = false;
    if (keycloakAdminClient.isEnabled()) {
      keycloakAdminClient.deleteUser(user.getOidcSubject());
      identityDeleted = true;
    } else {
      log.warn(
          "Keycloak admin access is off: the login account {} was not deleted",
          user.getOidcSubject());
    }

    log.info(
        "Deleted account of user id={} and {} tenant(s) only they belonged to",
        user.getId(),
        soleTenantIds.size());
    return identityDeleted;
  }

  private void revokeAppleSignIn(User user, String userAccessToken) {
    if (!appleSignInClient.isEnabled()) {
      return;
    }
    if (!keycloakAdminClient.isEnabled()) {
      log.warn("Apple token revocation needs Keycloak admin access, which is off");
      return;
    }
    String alias = appleSignInClient.identityProviderAlias();
    if (!keycloakAdminClient.hasIdentityProviderLink(user.getOidcSubject(), alias)) {
      return;
    }
    keycloakAdminClient
        .fetchStoredIdentityProviderToken(alias, userAccessToken)
        .ifPresentOrElse(
            appleSignInClient::revoke,
            () ->
                log.warn(
                    "User id={} signed in with Apple, but Keycloak stored no Apple token: not"
                        + " revoked",
                    user.getId()));
  }

  private void deleteTenant(Integer tenantId) {
    // Monitors first: the database cascades to their check results, status, alert history,
    // rollups and outages. The deletes by tenant ID catch rows the cascade does not reach.
    int monitors = monitorRepository.deleteAllByTenantId(tenantId);
    checkResultRepository.deleteAllByTenantId(tenantId);
    monitorStatusRepository.deleteAllByTenantId(tenantId);
    alertHistoryRepository.deleteAllByTenantId(tenantId);
    cleanupJobRepository.deleteAllByTenantId(tenantId);
    // Cascades to the alert contacts.
    tenantRepository.deleteTenantById(tenantId);
    log.info("Deleted tenant {} with {} monitor(s)", tenantId, monitors);
  }
}
