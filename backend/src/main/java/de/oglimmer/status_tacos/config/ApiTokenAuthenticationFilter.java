/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import de.oglimmer.status_tacos.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs in a request with an API token. The token comes as "Authorization: Bearer &lt;token&gt;" or
 * as the password of HTTP basic auth (any user name): Grafana supports both.
 *
 * <p>Not a bean: Spring Boot would add a bean filter to every request. SecurityConfig adds it to
 * the /prom filter chain only.
 */
@RequiredArgsConstructor
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

  private final ApiTokenService apiTokenService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = extractToken(request.getHeader(HttpHeaders.AUTHORIZATION));
    if (token != null) {
      apiTokenService
          .authenticate(token)
          .ifPresent(
              principal ->
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          UsernamePasswordAuthenticationToken.authenticated(
                              principal, null, List.of())));
    }
    chain.doFilter(request, response);
  }

  static String extractToken(String header) {
    if (header == null) {
      return null;
    }
    if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return header.substring(7).trim();
    }
    if (header.regionMatches(true, 0, "Basic ", 0, 6)) {
      try {
        String decoded =
            new String(
                Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        int colon = decoded.indexOf(':');
        return colon < 0 ? null : decoded.substring(colon + 1);
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
    return null;
  }
}
