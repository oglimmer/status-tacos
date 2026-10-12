/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.AccessConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Rejects tokens whose email + provider is not in monitor.access.allowed-logins. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AllowedLoginValidator implements OAuth2TokenValidator<Jwt> {

  /** OAuth2 error code of a token whose email + provider is not on the allow-list. */
  public static final String ERROR_CODE = "login_not_allowed";

  private final AccessConfig config;

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (config.getAllowedLogins().isEmpty() || isAllowed(jwt)) {
      return OAuth2TokenValidatorResult.success();
    }
    log.warn("Rejected login of subject {}: email and provider are not allowed", jwt.getSubject());
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(ERROR_CODE, "This login is not allowed", null));
  }

  private boolean isAllowed(Jwt jwt) {
    String email = jwt.getClaimAsString("email");
    String provider = jwt.getClaimAsString(config.getProviderClaim());
    if (email == null || provider == null) {
      return false;
    }
    return config.getAllowedLogins().stream()
        .anyMatch(
            allowed ->
                allowed.getEmail().equalsIgnoreCase(email)
                    && allowed.getProvider().equalsIgnoreCase(provider));
  }
}
