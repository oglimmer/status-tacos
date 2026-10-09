/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.oglimmer.status_tacos.config.KeycloakAdminConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Keycloak calls for account deletion. User lookups and deletes go through the Keycloak Admin REST
 * API, as the admin client's service account.
 */
@Slf4j
@Service
public class KeycloakAdminClient {

  private final KeycloakAdminConfig config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public KeycloakAdminClient(KeycloakAdminConfig config) {
    this(config, HttpClient.newBuilder().connectTimeout(config.getTimeout()).build());
  }

  KeycloakAdminClient(KeycloakAdminConfig config, HttpClient httpClient) {
    this.config = config;
    this.httpClient = httpClient;
  }

  public boolean isEnabled() {
    return config.isEnabled();
  }

  /**
   * Deletes the Keycloak user. The ID is the "sub" claim of the user's tokens. A user that does not
   * exist anymore counts as deleted.
   *
   * @throws IllegalStateException if Keycloak refuses or cannot be reached
   */
  public void deleteUser(String userId) {
    String accessToken = fetchAccessToken();
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    config.getBaseUrl()
                        + "/admin/realms/"
                        + encode(config.getRealm())
                        + "/users/"
                        + encode(userId)))
            .timeout(config.getTimeout())
            .header("Authorization", "Bearer " + accessToken)
            .DELETE()
            .build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() == 404) {
      log.info("Keycloak user {} does not exist anymore", userId);
      return;
    }
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException(
          "Keycloak refused to delete user " + userId + ": HTTP " + response.statusCode());
    }
    log.info("Deleted Keycloak user {}", userId);
  }

  /** True if the Keycloak user is linked to the identity provider, for example "apple". */
  public boolean hasIdentityProviderLink(String userId, String identityProviderAlias) {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    config.getBaseUrl()
                        + "/admin/realms/"
                        + encode(config.getRealm())
                        + "/users/"
                        + encode(userId)
                        + "/federated-identity"))
            .timeout(config.getTimeout())
            .header("Authorization", "Bearer " + fetchAccessToken())
            .GET()
            .build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() == 404) {
      return false;
    }
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Keycloak refused to list the identity provider links of user "
              + userId
              + ": HTTP "
              + response.statusCode());
    }
    try {
      for (JsonNode link : objectMapper.readTree(response.body())) {
        if (identityProviderAlias.equals(link.path("identityProvider").asText())) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      throw new IllegalStateException("Keycloak identity provider links are not JSON", e);
    }
  }

  /**
   * The token response of the identity provider that Keycloak stored at the user's last login
   * through it ("Store tokens" on the identity provider). Keycloak hands it out only to the user:
   * the call uses the user's access token, which needs the role broker/read-token.
   *
   * @return empty if Keycloak stored no token for the user
   */
  public Optional<String> fetchStoredIdentityProviderToken(
      String identityProviderAlias, String userAccessToken) {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    config.getBaseUrl()
                        + "/realms/"
                        + encode(config.getRealm())
                        + "/broker/"
                        + encode(identityProviderAlias)
                        + "/token"))
            .timeout(config.getTimeout())
            .header("Authorization", "Bearer " + userAccessToken)
            .GET()
            .build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() == 200) {
      return Optional.of(response.body());
    }
    if (response.statusCode() == 404) {
      return Optional.empty();
    }
    throw new IllegalStateException(
        "Keycloak refused to hand out the stored "
            + identityProviderAlias
            + " token: HTTP "
            + response.statusCode()
            + " "
            + response.body());
  }

  private String fetchAccessToken() {
    String form =
        "grant_type=client_credentials&client_id="
            + encode(config.getClientId())
            + "&client_secret="
            + encode(config.getClientSecret());
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    config.getBaseUrl()
                        + "/realms/"
                        + encode(config.getRealm())
                        + "/protocol/openid-connect/token"))
            .timeout(config.getTimeout())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Keycloak refused the admin client login: HTTP " + response.statusCode());
    }
    try {
      JsonNode token = objectMapper.readTree(response.body()).path("access_token");
      if (!token.isTextual() || token.asText().isEmpty()) {
        throw new IllegalStateException("Keycloak token response has no access_token");
      }
      return token.asText();
    } catch (IOException e) {
      throw new IllegalStateException("Keycloak token response is not JSON", e);
    }
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new IllegalStateException("Cannot reach Keycloak: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling Keycloak", e);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
