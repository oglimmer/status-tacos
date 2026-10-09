/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.oglimmer.status_tacos.config.KeycloakAdminConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the client against a local fake of Keycloak. */
class KeycloakAdminClientTest {

  private HttpServer server;
  private final List<Recorded> requests = new ArrayList<>();
  private volatile int tokenStatus = 200;
  private volatile int deleteStatus = 204;
  private volatile int brokerTokenStatus = 200;
  private volatile String federatedIdentities = "[]";
  private KeycloakAdminClient client;

  record Recorded(String method, String path, String authorization, String body) {}

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();

    KeycloakAdminConfig config = new KeycloakAdminConfig();
    config.setEnabled(true);
    config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    config.setRealm("status-tacos");
    config.setClientId("status-tacos-backend");
    config.setClientSecret("s3cret&x");
    client = new KeycloakAdminClient(config, HttpClient.newHttpClient());
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void logsInWithClientCredentialsAndDeletesTheUser() {
    client.deleteUser("1b2c-uuid");

    assertThat(requests).hasSize(2);
    Recorded token = requests.get(0);
    assertThat(token.method()).isEqualTo("POST");
    assertThat(token.path()).isEqualTo("/realms/status-tacos/protocol/openid-connect/token");
    assertThat(token.body())
        .isEqualTo(
            "grant_type=client_credentials&client_id=status-tacos-backend"
                + "&client_secret=s3cret%26x");
    Recorded delete = requests.get(1);
    assertThat(delete.method()).isEqualTo("DELETE");
    assertThat(delete.path()).isEqualTo("/admin/realms/status-tacos/users/1b2c-uuid");
    assertThat(delete.authorization()).isEqualTo("Bearer admin-token");
  }

  @Test
  void treatsAMissingUserAsDeleted() {
    deleteStatus = 404;

    client.deleteUser("gone");

    assertThat(requests).hasSize(2);
  }

  @Test
  void failsWhenKeycloakRefusesTheDelete() {
    deleteStatus = 403;

    assertThatThrownBy(() -> client.deleteUser("1b2c-uuid"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HTTP 403");
  }

  @Test
  void failsWhenTheAdminClientCannotLogIn() {
    tokenStatus = 401;

    assertThatThrownBy(() -> client.deleteUser("1b2c-uuid"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HTTP 401");
    assertThat(requests).hasSize(1);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    requests.add(
        new Recorded(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Authorization"),
            body));
    String path = exchange.getRequestURI().getPath();
    if (path.endsWith("/protocol/openid-connect/token")) {
      respond(exchange, tokenStatus, "{\"access_token\":\"admin-token\",\"expires_in\":60}");
    } else if (path.endsWith("/broker/apple/token")) {
      respond(exchange, brokerTokenStatus, "{\"refresh_token\":\"apple-refresh\"}");
    } else if (path.endsWith("/federated-identity")) {
      respond(exchange, 200, federatedIdentities);
    } else {
      exchange.sendResponseHeaders(deleteStatus, -1);
    }
    exchange.close();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] response = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, response.length);
    exchange.getResponseBody().write(response);
  }

  @Test
  void findsTheAppleLinkOfAUser() {
    federatedIdentities =
        "[{\"identityProvider\":\"google\",\"userId\":\"g1\"},"
            + "{\"identityProvider\":\"apple\",\"userId\":\"a1\"}]";

    assertThat(client.hasIdentityProviderLink("1b2c-uuid", "apple")).isTrue();
    assertThat(client.hasIdentityProviderLink("1b2c-uuid", "microsoft")).isFalse();
    Recorded list = requests.get(1);
    assertThat(list.method()).isEqualTo("GET");
    assertThat(list.path())
        .isEqualTo("/admin/realms/status-tacos/users/1b2c-uuid/federated-identity");
    assertThat(list.authorization()).isEqualTo("Bearer admin-token");
  }

  @Test
  void fetchesTheStoredTokenWithTheUsersAccessToken() {
    assertThat(client.fetchStoredIdentityProviderToken("apple", "user-token"))
        .contains("{\"refresh_token\":\"apple-refresh\"}");

    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).path()).isEqualTo("/realms/status-tacos/broker/apple/token");
    assertThat(requests.get(0).authorization()).isEqualTo("Bearer user-token");
  }

  @Test
  void returnsNothingWhenNoTokenIsStored() {
    brokerTokenStatus = 404;

    assertThat(client.fetchStoredIdentityProviderToken("apple", "user-token")).isEmpty();
  }

  @Test
  void failsWhenTheUserMayNotReadTheStoredToken() {
    brokerTokenStatus = 403;

    assertThatThrownBy(() -> client.fetchStoredIdentityProviderToken("apple", "user-token"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HTTP 403");
  }
}
