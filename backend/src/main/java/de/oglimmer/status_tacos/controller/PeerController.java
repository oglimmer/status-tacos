/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.config.PeerConfig;
import de.oglimmer.status_tacos.dto.PeerCheckRequestDto;
import de.oglimmer.status_tacos.dto.PeerCheckResponseDto;
import de.oglimmer.status_tacos.dto.PeerDistressDto;
import de.oglimmer.status_tacos.dto.PeerHeartbeatDto;
import de.oglimmer.status_tacos.service.AlertDeliveryHealthService;
import de.oglimmer.status_tacos.service.HttpClientService;
import de.oglimmer.status_tacos.service.MonitorExecutionService;
import de.oglimmer.status_tacos.service.PeerService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the other Status Tacos server, with a PEER API token (see SecurityConfig). Not under
 * /v1: no Keycloak sign-in.
 */
@Slf4j
@RestController
@RequestMapping("/peer")
@RequiredArgsConstructor
public class PeerController {

  /** A distress call checks the peer monitor at most this often. */
  static final Duration DISTRESS_MIN_GAP = Duration.ofSeconds(10);

  private final AlertDeliveryHealthService deliveryHealth;
  private final HttpClientService httpClientService;
  private final MonitorExecutionService monitorExecutionService;
  private final PeerService peerService;
  private final PeerConfig peerConfig;
  private final Clock clock;

  private long windowStartMs;
  private int checksInWindow;
  private long lastDistressMs = Long.MIN_VALUE / 2;

  /** The peer monitor of the other server checks this. Body regex: "status":"ok". */
  @GetMapping("/heartbeat")
  public PeerHeartbeatDto heartbeat() {
    AlertDeliveryHealthService.Health health = deliveryHealth.health();
    return health.canAlert()
        ? new PeerHeartbeatDto(PeerHeartbeatDto.OK, null, null)
        : new PeerHeartbeatDto(PeerHeartbeatDto.CANNOT_ALERT, health.since(), health.reason());
  }

  /** Checks a monitor of the other server once, before it sends a DOWN alert. Saves nothing. */
  @PostMapping("/check")
  public ResponseEntity<PeerCheckResponseDto> check(
      @Valid @RequestBody PeerCheckRequestDto request) {
    if (!takeCheckPermit()) {
      log.warn(
          "Other server asked for more than {} checks a minute",
          peerConfig.getMaxChecksPerMinute());
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
    HttpClientService.HttpCheckResult result =
        httpClientService.performHealthCheck(
            request.url(),
            request.httpHeaders(),
            request.statusCodeRegex(),
            request.responseBodyRegex(),
            request.prometheusKey(),
            request.prometheusMinValue(),
            request.prometheusMaxValue(),
            request.connectTimeout() != null ? Duration.ofSeconds(request.connectTimeout()) : null,
            Boolean.TRUE.equals(request.useSystemDns()));
    return ResponseEntity.ok(
        new PeerCheckResponseDto(
            Boolean.TRUE.equals(result.getIsUp()),
            result.getStatusCode(),
            result.getResponseTimeMs(),
            result.getErrorMessage()));
  }

  /** "Check me now": the other server cannot alert, or can again. Checks the peer monitor now. */
  @PostMapping("/distress")
  public ResponseEntity<Void> distress(@RequestBody(required = false) PeerDistressDto distress) {
    log.info(
        "Other server says: {}",
        distress != null && distress.status() != null ? distress.status() : "check me");
    if (!takeDistressPermit()) {
      return ResponseEntity.accepted().build();
    }
    PeerService.PeerSetup setup = peerService.setup();
    if (setup.monitor() == null) {
      log.warn("Distress call, but no single peer monitor ({})", setup.state());
      return ResponseEntity.accepted().build();
    }
    monitorExecutionService.executeMonitorCheckAsync(setup.monitor());
    return ResponseEntity.accepted().build();
  }

  private synchronized boolean takeCheckPermit() {
    long now = clock.millis();
    if (now - windowStartMs >= Duration.ofMinutes(1).toMillis()) {
      windowStartMs = now;
      checksInWindow = 0;
    }
    if (checksInWindow >= peerConfig.getMaxChecksPerMinute()) {
      return false;
    }
    checksInWindow++;
    return true;
  }

  private synchronized boolean takeDistressPermit() {
    long now = clock.millis();
    if (now - lastDistressMs < DISTRESS_MIN_GAP.toMillis()) {
      return false;
    }
    lastDistressMs = now;
    return true;
  }
}
