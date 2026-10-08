/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class UserTenantResolverTest {

  private static final String SUBJECT = "kc-subject";

  @Mock private UserRepository userRepository;
  @Mock private TenantService tenantService;

  @InjectMocks private UserTenantResolver resolver;

  @BeforeEach
  void signIn() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(SUBJECT)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    // With authorities, the token counts as authenticated.
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void unknownUser_getsNoTenants() {
    // Before the fix, a signed-in user without a user record saw the data of tenant 1.
    when(userRepository.findByOidcSubjectAndIsActiveTrue(SUBJECT)).thenReturn(Optional.empty());

    assertThat(resolver.getCurrentUserTenantIds()).isEmpty();
    assertThat(resolver.hasAccessToTenant(1)).isFalse();
  }

  @Test
  void userWithoutTenants_getsNoTenants() {
    when(userRepository.findByOidcSubjectAndIsActiveTrue(SUBJECT))
        .thenReturn(Optional.of(User.builder().oidcSubject(SUBJECT).tenants(Set.of()).build()));

    assertThat(resolver.getCurrentUserTenantIds()).isEmpty();
  }

  @Test
  void userWithNullTenants_getsNoTenants() {
    when(userRepository.findByOidcSubjectAndIsActiveTrue(SUBJECT))
        .thenReturn(Optional.of(User.builder().oidcSubject(SUBJECT).tenants(null).build()));

    assertThat(resolver.getCurrentUserTenantIds()).isEmpty();
  }

  @Test
  void user_getsTheIdsOfItsTenants() {
    User user =
        User.builder()
            .oidcSubject(SUBJECT)
            .tenants(Set.of(Tenant.builder().id(7).build(), Tenant.builder().id(9).build()))
            .build();
    when(userRepository.findByOidcSubjectAndIsActiveTrue(SUBJECT)).thenReturn(Optional.of(user));

    assertThat(resolver.getCurrentUserTenantIds()).containsExactlyInAnyOrder(7, 9);
    assertThat(resolver.hasAccessToTenant(1)).isFalse();
  }

  @Test
  void noAuthentication_getsNoTenants() {
    SecurityContextHolder.clearContext();

    assertThat(resolver.getCurrentUserTenantIds()).isEmpty();
  }
}
