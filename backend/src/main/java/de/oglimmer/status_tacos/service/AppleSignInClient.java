/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.oglimmer.status_tacos.config.AppleSignInConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Revokes Sign in with Apple tokens. See "Revoke tokens" in the Sign in with Apple REST API
 * documentation.
 */
@Slf4j
@Service
public class AppleSignInClient {

  private static final String AUDIENCE = "https://appleid.apple.com";

  /** The client secret is only used for one request. Apple allows up to 6 months. */
  private static final Duration CLIENT_SECRET_LIFETIME = Duration.ofMinutes(5);

  private final AppleSignInConfig config;
  private final HttpClient httpClient;
  private final Clock clock;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public AppleSignInClient(AppleSignInConfig config) {
    this(
        config,
        HttpClient.newBuilder().connectTimeout(config.getTimeout()).build(),
        Clock.systemUTC());
  }

  AppleSignInClient(AppleSignInConfig config, HttpClient httpClient, Clock clock) {
    this.config = config;
    this.httpClient = httpClient;
    this.clock = clock;
  }

  public boolean isEnabled() {
    return config.isEnabled();
  }

  public String identityProviderAlias() {
    return config.getIdentityProviderAlias();
  }

  /**
   * Revokes the tokens of the token response that Keycloak stored for the user. The refresh token
   * is preferred: revoking it ends the user's authorization of the app. A token that Apple does not
   * know anymore counts as revoked.
   *
   * @param storedTokenResponse the JSON of Apple's token response, as Keycloak stores it
   * @throws IllegalStateException if Apple refuses or cannot be reached
   */
  public void revoke(String storedTokenResponse) {
    JsonNode tokens;
    try {
      tokens = objectMapper.readTree(storedTokenResponse);
    } catch (IOException e) {
      throw new IllegalStateException("The stored Apple token response is not JSON", e);
    }
    String tokenTypeHint = "refresh_token";
    String token = tokens.path("refresh_token").asText("");
    if (token.isEmpty()) {
      tokenTypeHint = "access_token";
      token = tokens.path("access_token").asText("");
    }
    if (token.isEmpty()) {
      log.warn("The stored Apple token response has no token to revoke");
      return;
    }

    String form =
        "client_id="
            + encode(config.getClientId())
            + "&client_secret="
            + encode(clientSecret())
            + "&token="
            + encode(token)
            + "&token_type_hint="
            + tokenTypeHint;
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(config.getRevokeUrl()))
            .timeout(config.getTimeout())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() == 200) {
      log.info("Revoked the Apple {}", tokenTypeHint);
      return;
    }
    if (response.statusCode() == 400 && "invalid_grant".equals(error(response.body()))) {
      // Expired, or revoked by the user in the Apple ID settings.
      log.info("Apple does not know the {} anymore, nothing to revoke", tokenTypeHint);
      return;
    }
    throw new IllegalStateException(
        "Apple refused to revoke the token: HTTP "
            + response.statusCode()
            + " "
            + error(response.body()));
  }

  /** ES256 JWT signed with the .p8 key, as Apple wants it for its client secret. */
  String clientSecret() {
    if (config.getPrivateKey() == null || config.getPrivateKey().isBlank()) {
      throw new IllegalStateException("monitor.apple-sign-in.private-key is not set");
    }
    Instant now = clock.instant();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(config.getKeyId()).build(),
            new JWTClaimsSet.Builder()
                .issuer(config.getTeamId())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(CLIENT_SECRET_LIFETIME)))
                .audience(AUDIENCE)
                .subject(config.getClientId())
                .build());
    try {
      jwt.sign(new ECDSASigner(ApnsClient.parsePrivateKey(config.getPrivateKey())));
    } catch (GeneralSecurityException | JOSEException e) {
      throw new IllegalStateException("Cannot sign the Apple client secret: " + e.getMessage(), e);
    }
    return jwt.serialize();
  }

  private String error(String body) {
    try {
      return objectMapper.readTree(body).path("error").asText("");
    } catch (IOException e) {
      return "";
    }
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new IllegalStateException("Cannot reach Apple: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling Apple", e);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
