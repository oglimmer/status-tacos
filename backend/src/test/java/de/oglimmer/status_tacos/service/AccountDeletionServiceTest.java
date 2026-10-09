/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.AlertHistoryRepository;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import de.oglimmer.status_tacos.repository.CleanupJobRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.MonitorStatusRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

  private static final int OWN_TENANT_ID = 1;
  private static final int SHARED_TENANT_ID = 2;
  private static final String USER_TOKEN = "user-access-token";

  @Mock private UserRepository userRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private MonitorRepository monitorRepository;
  @Mock private CheckResultRepository checkResultRepository;
  @Mock private MonitorStatusRepository monitorStatusRepository;
  @Mock private AlertHistoryRepository alertHistoryRepository;
  @Mock private CleanupJobRepository cleanupJobRepository;
  @Mock private KeycloakAdminClient keycloakAdminClient;
  @Mock private AppleSignInClient appleSignInClient;

  @InjectMocks private AccountDeletionService accountDeletionService;

  private User user;

  @BeforeEach
  void setUp() {
    user =
        User.builder()
            .id(7)
            .email("ann@example.com")
            .oidcSubject("kc-subject-7")
            .tenants(
                Set.of(
                    Tenant.builder().id(OWN_TENANT_ID).build(),
                    Tenant.builder().id(SHARED_TENANT_ID).build()))
            .build();
    lenient().when(userRepository.countMembersOfTenant(OWN_TENANT_ID)).thenReturn(1L);
    lenient().when(userRepository.countMembersOfTenant(SHARED_TENANT_ID)).thenReturn(3L);
  }

  @Test
  void deletesUserAndOnlyTheTenantsNobodyElseBelongsTo() {
    when(keycloakAdminClient.isEnabled()).thenReturn(true);

    boolean loginDeleted = accountDeletionService.deleteAccount(user, USER_TOKEN);

    assertThat(loginDeleted).isTrue();
    InOrder order = inOrder(userRepository, monitorRepository, tenantRepository);
    order.verify(userRepository).deleteUserById(7);
    order.verify(monitorRepository).deleteAllByTenantId(OWN_TENANT_ID);
    order.verify(tenantRepository).deleteTenantById(OWN_TENANT_ID);
    verify(checkResultRepository).deleteAllByTenantId(OWN_TENANT_ID);
    verify(monitorStatusRepository).deleteAllByTenantId(OWN_TENANT_ID);
    verify(alertHistoryRepository).deleteAllByTenantId(OWN_TENANT_ID);
    verify(cleanupJobRepository).deleteAllByTenantId(OWN_TENANT_ID);
    verify(monitorRepository, never()).deleteAllByTenantId(SHARED_TENANT_ID);
    verify(tenantRepository, never()).deleteTenantById(SHARED_TENANT_ID);
    verify(keycloakAdminClient).deleteUser("kc-subject-7");
  }

  @Test
  void keepsTheLoginAccountWhenKeycloakAdminIsOff() {
    when(keycloakAdminClient.isEnabled()).thenReturn(false);

    boolean loginDeleted = accountDeletionService.deleteAccount(user, USER_TOKEN);

    assertThat(loginDeleted).isFalse();
    verify(userRepository).deleteUserById(7);
    verify(keycloakAdminClient, never()).deleteUser(anyString());
  }

  @Test
  void passesKeycloakErrorsOnSoTheTransactionRollsBack() {
    when(keycloakAdminClient.isEnabled()).thenReturn(true);
    doThrow(new IllegalStateException("HTTP 403"))
        .when(keycloakAdminClient)
        .deleteUser("kc-subject-7");

    assertThatThrownBy(() -> accountDeletionService.deleteAccount(user, USER_TOKEN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("403");
  }

  @Test
  void revokesTheAppleTokensBeforeDeletingAnything() {
    stubAppleUser();
    when(keycloakAdminClient.fetchStoredIdentityProviderToken("apple", USER_TOKEN))
        .thenReturn(Optional.of("{\"refresh_token\":\"r\"}"));

    accountDeletionService.deleteAccount(user, USER_TOKEN);

    InOrder order = inOrder(appleSignInClient, userRepository, keycloakAdminClient);
    order.verify(appleSignInClient).revoke("{\"refresh_token\":\"r\"}");
    order.verify(userRepository).deleteUserById(7);
    order.verify(keycloakAdminClient).deleteUser("kc-subject-7");
  }

  @Test
  void deletesNothingWhenAppleRefusesTheRevocation() {
    stubAppleUser();
    when(keycloakAdminClient.fetchStoredIdentityProviderToken("apple", USER_TOKEN))
        .thenReturn(Optional.of("{\"refresh_token\":\"r\"}"));
    doThrow(new IllegalStateException("HTTP 500")).when(appleSignInClient).revoke(anyString());

    assertThatThrownBy(() -> accountDeletionService.deleteAccount(user, USER_TOKEN))
        .isInstanceOf(IllegalStateException.class);
    verify(userRepository, never()).deleteUserById(7);
    verify(keycloakAdminClient, never()).deleteUser(anyString());
  }

  @Test
  void deletesAnAppleUserWithoutStoredTokenWithoutRevocation() {
    stubAppleUser();
    when(keycloakAdminClient.fetchStoredIdentityProviderToken("apple", USER_TOKEN))
        .thenReturn(Optional.empty());

    accountDeletionService.deleteAccount(user, USER_TOKEN);

    verify(appleSignInClient, never()).revoke(anyString());
    verify(userRepository).deleteUserById(7);
  }

  @Test
  void doesNotAskForAppleTokensOfUsersWithoutAppleLink() {
    when(appleSignInClient.isEnabled()).thenReturn(true);
    when(appleSignInClient.identityProviderAlias()).thenReturn("apple");
    when(keycloakAdminClient.isEnabled()).thenReturn(true);
    when(keycloakAdminClient.hasIdentityProviderLink("kc-subject-7", "apple")).thenReturn(false);

    accountDeletionService.deleteAccount(user, USER_TOKEN);

    verify(keycloakAdminClient, never()).fetchStoredIdentityProviderToken(anyString(), anyString());
    verify(userRepository).deleteUserById(7);
  }

  private void stubAppleUser() {
    when(appleSignInClient.isEnabled()).thenReturn(true);
    when(appleSignInClient.identityProviderAlias()).thenReturn("apple");
    when(keycloakAdminClient.isEnabled()).thenReturn(true);
    when(keycloakAdminClient.hasIdentityProviderLink("kc-subject-7", "apple")).thenReturn(true);
  }
}
