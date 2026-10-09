/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.oglimmer.status_tacos.config.AppleSignInConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the client against a local fake of Apple's revoke endpoint. */
class AppleSignInClientTest {

  private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

  private HttpServer server;
  private final List<Map<String, String>> forms = new ArrayList<>();
  private volatile int status = 200;
  private volatile String responseBody = "";
  private KeyPair keyPair;
  private AppleSignInClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/auth/revoke", this::handle);
    server.start();

    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    keyPair = generator.generateKeyPair();

    AppleSignInConfig config = new AppleSignInConfig();
    config.setEnabled(true);
    config.setClientId("de.oglimmer.statustacos.signin");
    config.setTeamId("TEAM123456");
    config.setKeyId("KEY1234567");
    config.setPrivateKey(
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n");
    config.setRevokeUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/auth/revoke");
    client =
        new AppleSignInClient(config, HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void revokesTheRefreshTokenWithASignedClientSecret() throws Exception {
    client.revoke("{\"access_token\":\"a\",\"refresh_token\":\"r.1\",\"id_token\":\"i\"}");

    assertThat(forms).hasSize(1);
    Map<String, String> form = forms.get(0);
    assertThat(form)
        .containsEntry("client_id", "de.oglimmer.statustacos.signin")
        .containsEntry("token", "r.1")
        .containsEntry("token_type_hint", "refresh_token");

    SignedJWT secret = SignedJWT.parse(form.get("client_secret"));
    assertThat(secret.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
    assertThat(secret.getHeader().getKeyID()).isEqualTo("KEY1234567");
    assertThat(secret.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM123456");
    assertThat(secret.getJWTClaimsSet().getSubject()).isEqualTo("de.oglimmer.statustacos.signin");
    assertThat(secret.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
    assertThat(secret.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(NOW);
    assertThat(secret.getJWTClaimsSet().getExpirationTime().toInstant()).isAfter(NOW);
  }

  @Test
  void fallsBackToTheAccessToken() {
    client.revoke("{\"access_token\":\"a.1\"}");

    assertThat(forms.get(0))
        .containsEntry("token", "a.1")
        .containsEntry("token_type_hint", "access_token");
  }

  @Test
  void treatsATokenAppleDoesNotKnowAsRevoked() {
    status = 400;
    responseBody = "{\"error\":\"invalid_grant\"}";

    client.revoke("{\"refresh_token\":\"old\"}");

    assertThat(forms).hasSize(1);
  }

  @Test
  void failsWhenAppleRefuses() {
    status = 400;
    responseBody = "{\"error\":\"invalid_client\"}";

    assertThatThrownBy(() -> client.revoke("{\"refresh_token\":\"r\"}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HTTP 400")
        .hasMessageContaining("invalid_client");
  }

  @Test
  void sendsNothingWithoutAToken() {
    client.revoke("{\"id_token\":\"i\"}");

    assertThat(forms).isEmpty();
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Map<String, String> form = new HashMap<>();
    for (String pair : body.split("&")) {
      String[] parts = pair.split("=", 2);
      form.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
    }
    forms.add(form);
    byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
    if (response.length > 0) {
      exchange.getResponseBody().write(response);
    }
    exchange.close();
  }
}
