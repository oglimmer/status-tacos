/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import de.oglimmer.status_tacos.service.AllowedLoginValidator;
import de.oglimmer.status_tacos.service.ApiTokenService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
  private String jwkSetUri;

  /**
   * The public status pages: no sign-in, and no bearer token check, so a stale token in a browser
   * cannot break them. Any site may embed the HTML page in a frame: its own CSP header allows it
   * and puts it in a sandbox, so X-Frame-Options is off here. The controller sets Cache-Control.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain publicStatusFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/v1/public/**")
        .authorizeHttpRequests(
            authz -> authz.requestMatchers(HttpMethod.GET).permitAll().anyRequest().denyAll())
        .headers(
            headers ->
                headers
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                    .cacheControl(HeadersConfigurer.CacheControlConfig::disable))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  /**
   * The Prometheus-compatible query API for Grafana: signed in with an API token, not with
   * Keycloak. Grafana sends POST form requests, so CSRF is off (no cookies, no session).
   */
  @Bean
  @Order(2)
  public SecurityFilterChain promFilterChain(HttpSecurity http, ApiTokenService apiTokenService)
      throws Exception {
    http.securityMatcher("/prom/**")
        .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
        .addFilterBefore(
            new ApiTokenAuthenticationFilter(apiTokenService), AnonymousAuthenticationFilter.class)
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  @Order(3)
  public SecurityFilterChain filterChain(
      HttpSecurity http, LoginNotAllowedEntryPoint loginNotAllowedEntryPoint) throws Exception {
    http.authorizeHttpRequests(
            authz ->
                authz
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/v1/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> {}).authenticationEntryPoint(loginNotAllowedEntryPoint))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable);

    return http.build();
  }

  @Bean
  @Profile("!test") // Only create this bean when NOT in test profile
  public JwtDecoder jwtDecoder(AllowedLoginValidator allowedLoginValidator) {
    if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefault(), allowedLoginValidator));
      return decoder;
    }
    throw new IllegalStateException("JWK Set URI is not configured");
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of("*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
