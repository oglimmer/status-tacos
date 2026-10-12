/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.oglimmer.status_tacos.service.AllowedLoginValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

class LoginNotAllowedEntryPointTest {

  private final LoginNotAllowedEntryPoint entryPoint = new LoginNotAllowedEntryPoint();

  /** The exception that Spring Security throws when a JwtDecoder validator fails. */
  private static AuthenticationException authenticate(String errorCode) {
    JwtAuthenticationProvider provider =
        new JwtAuthenticationProvider(
            token -> {
              throw new JwtValidationException(
                  "invalid", List.of(new OAuth2Error(errorCode, "description", null)));
            });
    return assertThrows(
        AuthenticationException.class,
        () -> provider.authenticate(new BearerTokenAuthenticationToken("token")));
  }

  @Test
  void loginNotAllowedGets401WithErrorBody() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    entryPoint.commence(
        new MockHttpServletRequest(), response, authenticate(AllowedLoginValidator.ERROR_CODE));

    assertEquals(401, response.getStatus());
    assertEquals("application/json", response.getContentType());
    assertEquals(LoginNotAllowedEntryPoint.BODY, response.getContentAsString());
    assertTrue(response.getHeader("WWW-Authenticate").startsWith("Bearer"));
  }

  @Test
  void otherInvalidTokenGets401WithoutBody() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    entryPoint.commence(new MockHttpServletRequest(), response, authenticate("invalid_token"));

    assertEquals(401, response.getStatus());
    assertEquals("", response.getContentAsString());
  }
}
