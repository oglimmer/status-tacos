/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.oglimmer.status_tacos.config.ApnsConfig;
import de.oglimmer.status_tacos.persistence.PushDevice;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the client against a local fake of APNs. */
class ApnsClientTest {

  private static final String TOKEN = "a".repeat(64);
  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  private HttpServer server;
  private final List<Recorded> requests = new ArrayList<>();
  private volatile int status = 200;
  private volatile String responseBody = "";
  private KeyPair keyPair;
  private ApnsConfig config;
  private MutableClock clock;
  private ApnsClient client;

  record Recorded(String path, Map<String, List<String>> headers, String body) {}

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();

    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    keyPair = generator.generateKeyPair();

    config = new ApnsConfig();
    config.setEnabled(true);
    config.setTeamId("TEAM123456");
    config.setKeyId("KEY1234567");
    config.setBundleId("de.oglimmer.statustacos");
    config.setPrivateKey(pem(keyPair));
    config.setProductionUrl(base + "/production");
    config.setSandboxUrl(base + "/sandbox");

    clock = new MutableClock(NOW);
    // HTTP/1.1: the fake server does not speak HTTP/2.
    client =
        new ApnsClient(
            config, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(), clock);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void sendsAnAlertWithAllApnsHeaders() throws Exception {
    PushMessage message =
        new PushMessage("Down: Shop", "HTTP 503 · Prod", 7, 1, "down", "monitor-7", true);

    assertThat(client.send(device(PushDevice.Environment.PRODUCTION), message))
        .isEqualTo(ApnsClient.Outcome.DELIVERED);

    Recorded request = requests.getFirst();
    assertThat(request.path()).isEqualTo("/production/3/device/" + TOKEN);
    assertThat(header(request, "apns-topic")).isEqualTo("de.oglimmer.statustacos");
    assertThat(header(request, "apns-push-type")).isEqualTo("alert");
    assertThat(header(request, "apns-priority")).isEqualTo("10");
    assertThat(header(request, "apns-collapse-id")).isEqualTo("monitor-7");
    assertThat(header(request, "apns-expiration"))
        .isEqualTo(String.valueOf(NOW.plus(Duration.ofHours(1)).getEpochSecond()));

    JsonNode body = new ObjectMapper().readTree(request.body());
    assertThat(body.at("/aps/alert/title").asText()).isEqualTo("Down: Shop");
    assertThat(body.at("/aps/alert/body").asText()).isEqualTo("HTTP 503 · Prod");
    assertThat(body.at("/aps/interruption-level").asText()).isEqualTo("time-sensitive");
    assertThat(body.at("/aps/thread-id").asText()).isEqualTo("monitor-7");
    assertThat(body.get("monitorId").asInt()).isEqualTo(7);
    assertThat(body.get("kind").asText()).isEqualTo("down");
  }

  @Test
  void signsTheProviderTokenWithTheKey() throws Exception {
    client.send(device(PushDevice.Environment.PRODUCTION), message());

    String authorization = header(requests.getFirst(), "authorization");
    assertThat(authorization).startsWith("bearer ");
    SignedJWT jwt = SignedJWT.parse(authorization.substring("bearer ".length()));
    assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
    assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
    assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEY1234567");
    assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM123456");
    assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(NOW);
  }

  @Test
  void reusesTheProviderTokenFor50Minutes() throws Exception {
    String first = client.providerToken();
    clock.instant = NOW.plus(Duration.ofMinutes(49));
    assertThat(client.providerToken()).isEqualTo(first);
    clock.instant = NOW.plus(Duration.ofMinutes(51));
    assertThat(client.providerToken()).isNotEqualTo(first);
  }

  @Test
  void usesTheSandboxForDebugBuilds() {
    client.send(device(PushDevice.Environment.SANDBOX), message());

    assertThat(requests.getFirst().path()).isEqualTo("/sandbox/3/device/" + TOKEN);
  }

  @Test
  void reportsGoneDevices() {
    status = 410;
    responseBody = "{\"reason\":\"Unregistered\",\"timestamp\":1}";
    assertThat(client.send(device(PushDevice.Environment.PRODUCTION), message()))
        .isEqualTo(ApnsClient.Outcome.DEVICE_GONE);

    status = 400;
    responseBody = "{\"reason\":\"BadDeviceToken\"}";
    assertThat(client.send(device(PushDevice.Environment.PRODUCTION), message()))
        .isEqualTo(ApnsClient.Outcome.DEVICE_GONE);
  }

  @Test
  void otherErrorsKeepTheDevice() {
    status = 500;
    responseBody = "{\"reason\":\"InternalServerError\"}";
    assertThat(client.send(device(PushDevice.Environment.PRODUCTION), message()))
        .isEqualTo(ApnsClient.Outcome.FAILED);

    status = 403;
    responseBody = "{\"reason\":\"InvalidProviderToken\"}";
    assertThat(client.send(device(PushDevice.Environment.PRODUCTION), message()))
        .isEqualTo(ApnsClient.Outcome.FAILED);
  }

  @Test
  void aMissingKeyFailsWithoutThrowing() {
    config.setPrivateKey("");
    assertThat(client.send(device(PushDevice.Environment.PRODUCTION), message()))
        .isEqualTo(ApnsClient.Outcome.FAILED);
    assertThat(requests).isEmpty();
  }

  @Test
  void parsesP8Keys() throws Exception {
    assertThat(ApnsClient.parsePrivateKey(pem(keyPair))).isNotNull();
    assertThatThrownBy(() -> ApnsClient.parsePrivateKey(" "))
        .isInstanceOf(GeneralSecurityException.class);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    synchronized (requests) {
      requests.add(
          new Recorded(exchange.getRequestURI().getPath(), exchange.getRequestHeaders(), body));
    }
    byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
    if (response.length > 0) {
      exchange.getResponseBody().write(response);
    }
    exchange.close();
  }

  private static String header(Recorded request, String name) {
    return request.headers().entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(entry -> entry.getValue().getFirst())
        .findFirst()
        .orElse(null);
  }

  private static PushDevice device(PushDevice.Environment environment) {
    return PushDevice.builder().token(TOKEN).environment(environment).build();
  }

  private static PushMessage message() {
    return new PushMessage("Up again: Shop", "Was down for 5m", 7, 1, "up", "monitor-7", false);
  }

  private static String pem(KeyPair keyPair) {
    String base64 =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(keyPair.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
  }

  static class MutableClock extends Clock {
    volatile Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
