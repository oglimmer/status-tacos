/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.oglimmer.status_tacos.config.AccessConfig;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AllowedLoginValidatorTest {

  private final AccessConfig config = new AccessConfig();
  private final AllowedLoginValidator validator = new AllowedLoginValidator(config);

  private static Jwt jwt(String email, String provider) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "u1");
    if (email != null) {
      claims.put("email", email);
    }
    if (provider != null) {
      claims.put("identity_provider", provider);
    }
    return new Jwt(
        "t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
  }

  private void allow(String email, String provider) {
    AccessConfig.AllowedLogin login = new AccessConfig.AllowedLogin();
    login.setEmail(email);
    login.setProvider(provider);
    config.setAllowedLogins(List.of(login));
  }

  @Test
  void emptyListAcceptsEverybody() {
    assertFalse(validator.validate(jwt("a@b.c", "github")).hasErrors());
    assertFalse(validator.validate(jwt(null, null)).hasErrors());
  }

  @Test
  void acceptsListedPairIgnoringCase() {
    allow("Me@Example.com", "Google");
    assertFalse(validator.validate(jwt("me@example.com", "google")).hasErrors());
  }

  @Test
  void rejectsOtherEmailOtherProviderOrMissingClaims() {
    allow("me@example.com", "google");
    assertTrue(validator.validate(jwt("you@example.com", "google")).hasErrors());
    assertTrue(validator.validate(jwt("me@example.com", "apple")).hasErrors());
    assertTrue(validator.validate(jwt("me@example.com", null)).hasErrors());
    assertTrue(validator.validate(jwt(null, "google")).hasErrors());
  }
}
