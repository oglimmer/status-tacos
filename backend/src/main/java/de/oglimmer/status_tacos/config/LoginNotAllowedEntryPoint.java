/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import de.oglimmer.status_tacos.service.AllowedLoginValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers 401 like the default bearer token entry point. When the token is valid but its email +
 * provider is not on the allow-list, the body says so, so the clients can show a clear message.
 */
@Component
public class LoginNotAllowedEntryPoint implements AuthenticationEntryPoint {

  static final String BODY =
      "{\"error\":\""
          + AllowedLoginValidator.ERROR_CODE
          + "\",\"message\":\"This account is not allowed to use Status Tacos.\"}";

  private final BearerTokenAuthenticationEntryPoint delegate =
      new BearerTokenAuthenticationEntryPoint();

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    delegate.commence(request, response, authException);
    if (isLoginNotAllowed(authException)) {
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write(BODY);
    }
  }

  static boolean isLoginNotAllowed(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof JwtValidationException validation
          && validation.getErrors().stream()
              .anyMatch(error -> AllowedLoginValidator.ERROR_CODE.equals(error.getErrorCode()))) {
        return true;
      }
    }
    return false;
  }
}
