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
    if (exchange.getRequestURI().getPath().endsWith("/token")) {
      byte[] response =
          "{\"access_token\":\"admin-token\",\"expires_in\":60}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(tokenStatus, response.length);
      exchange.getResponseBody().write(response);
    } else {
      exchange.sendResponseHeaders(deleteStatus, -1);
    }
    exchange.close();
  }
}
