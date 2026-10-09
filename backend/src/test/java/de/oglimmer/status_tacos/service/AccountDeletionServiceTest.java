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

  @Mock private UserRepository userRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private MonitorRepository monitorRepository;
  @Mock private CheckResultRepository checkResultRepository;
  @Mock private MonitorStatusRepository monitorStatusRepository;
  @Mock private AlertHistoryRepository alertHistoryRepository;
  @Mock private CleanupJobRepository cleanupJobRepository;
  @Mock private KeycloakAdminClient keycloakAdminClient;

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
    when(userRepository.countMembersOfTenant(OWN_TENANT_ID)).thenReturn(1L);
    when(userRepository.countMembersOfTenant(SHARED_TENANT_ID)).thenReturn(3L);
  }

  @Test
  void deletesUserAndOnlyTheTenantsNobodyElseBelongsTo() {
    when(keycloakAdminClient.isEnabled()).thenReturn(true);

    boolean loginDeleted = accountDeletionService.deleteAccount(user);

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

    boolean loginDeleted = accountDeletionService.deleteAccount(user);

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

    assertThatThrownBy(() -> accountDeletionService.deleteAccount(user))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("403");
  }
}
