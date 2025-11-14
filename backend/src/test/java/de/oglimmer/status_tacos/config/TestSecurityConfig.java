/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
public class TestSecurityConfig {

  @Bean
  @Primary
  public JwtDecoder jwtDecoder() {
    // Return a mock JwtDecoder for tests that always returns a valid JWT
    return token -> {
      Map<String, Object> headers = new HashMap<>();
      headers.put("alg", "none");

      Map<String, Object> claims = new HashMap<>();
      claims.put("sub", "testuser");
      claims.put("scope", "read write");
      claims.put("tenantIds", "1");

      return new Jwt(token, Instant.now(), Instant.now().plusSeconds(3600), headers, claims);
    };
  }
}
