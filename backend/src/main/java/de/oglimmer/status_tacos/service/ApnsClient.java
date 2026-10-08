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
import de.oglimmer.status_tacos.config.ApnsConfig;
import de.oglimmer.status_tacos.persistence.PushDevice;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Sends notifications to APNs over HTTP/2 with a provider token (ES256 JWT signed with the .p8
 * key). See "Sending notification requests to APNs" in the Apple developer documentation.
 */
@Slf4j
@Service
public class ApnsClient {

  /** Apple refuses tokens older than 1 hour and new tokens more often than every 20 minutes. */
  private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(50);

  /** Reasons that mean the token will never work again: forget the device. */
  private static final Set<String> GONE_REASONS =
      Set.of("BadDeviceToken", "Unregistered", "DeviceTokenNotForTopic");

  private final ApnsConfig config;
  private final HttpClient httpClient;
  private final Clock clock;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private ECPrivateKey signingKey;
  private String providerToken;
  private Instant providerTokenIssuedAt;

  @Autowired
  public ApnsClient(ApnsConfig config) {
    this(
        config,
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(config.getTimeout())
            .build(),
        Clock.systemUTC());
  }

  ApnsClient(ApnsConfig config, HttpClient httpClient, Clock clock) {
    this.config = config;
    this.httpClient = httpClient;
    this.clock = clock;
  }

  public enum Outcome {
    DELIVERED,
    /** The token is not valid any more (app deleted, or wrong environment). */
    DEVICE_GONE,
    FAILED
  }

  /** Sends one notification. Never throws: problems are logged and returned as an outcome. */
  public Outcome send(PushDevice device, PushMessage message) {
    try {
      String baseUrl =
          device.getEnvironment() == PushDevice.Environment.SANDBOX
              ? config.getSandboxUrl()
              : config.getProductionUrl();
      Instant expiration = clock.instant().plus(config.getExpiration());

      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create(baseUrl + "/3/device/" + device.getToken()))
              .timeout(config.getTimeout())
              .header("authorization", "bearer " + providerToken())
              .header("apns-topic", config.getBundleId())
              .header("apns-push-type", "alert")
              .header("apns-priority", "10")
              .header("apns-expiration", String.valueOf(expiration.getEpochSecond()))
              .header("content-type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(message.toJson(objectMapper)));
      if (message.collapseId() != null) {
        request.header("apns-collapse-id", message.collapseId());
      }

      HttpResponse<String> response =
          httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        return Outcome.DELIVERED;
      }

      String reason = reason(response.body());
      if (response.statusCode() == 410 || GONE_REASONS.contains(reason)) {
        log.info("APNs: device {} is gone ({})", shortToken(device), reason);
        return Outcome.DEVICE_GONE;
      }
      if ("ExpiredProviderToken".equals(reason) || "InvalidProviderToken".equals(reason)) {
        resetProviderToken();
      }
      log.error(
          "APNs refused the notification for device {}: HTTP {} {}",
          shortToken(device),
          response.statusCode(),
          reason);
      return Outcome.FAILED;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("APNs request for device {} interrupted", shortToken(device));
      return Outcome.FAILED;
    } catch (IOException | RuntimeException | GeneralSecurityException | JOSEException e) {
      log.error("APNs request for device {} failed: {}", shortToken(device), e.getMessage(), e);
      return Outcome.FAILED;
    }
  }

  /** The provider token, signed again after {@link #TOKEN_LIFETIME}. */
  synchronized String providerToken() throws GeneralSecurityException, JOSEException {
    Instant now = clock.instant();
    if (providerToken == null || providerTokenIssuedAt.plus(TOKEN_LIFETIME).isBefore(now)) {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(config.getKeyId()).build(),
              new JWTClaimsSet.Builder()
                  .issuer(config.getTeamId())
                  .issueTime(Date.from(now))
                  .build());
      jwt.sign(new ECDSASigner(signingKey()));
      providerToken = jwt.serialize();
      providerTokenIssuedAt = now;
    }
    return providerToken;
  }

  private synchronized void resetProviderToken() {
    providerToken = null;
  }

  private synchronized ECPrivateKey signingKey() throws GeneralSecurityException {
    if (signingKey == null) {
      signingKey = parsePrivateKey(config.getPrivateKey());
    }
    return signingKey;
  }

  /** Reads a PKCS#8 PEM key, the format of the .p8 file. */
  static ECPrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
    if (pem == null || pem.isBlank()) {
      throw new GeneralSecurityException("monitor.apns.private-key is not set");
    }
    String base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(base64);
    return (ECPrivateKey)
        KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  private String reason(String body) {
    try {
      JsonNode node = objectMapper.readTree(body);
      return node.path("reason").asText("");
    } catch (IOException | RuntimeException e) {
      return "";
    }
  }

  /** Enough of the token to find it in the database, without logging the whole token. */
  private static String shortToken(PushDevice device) {
    String token = device.getToken();
    return token == null || token.length() <= 8 ? token : token.substring(0, 8) + "…";
  }
}
