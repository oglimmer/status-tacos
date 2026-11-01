/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.PeerConfig;
import de.oglimmer.status_tacos.dto.PeerCheckRequestDto;
import de.oglimmer.status_tacos.dto.PeerCheckResponseDto;
import de.oglimmer.status_tacos.dto.PeerDistressDto;
import de.oglimmer.status_tacos.dto.PeerHeartbeatDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.PeerResult;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Works together with the other Status Tacos server.
 *
 * <p>The peer monitor is the one ACTIVE or SILENT monitor in the peer tenant
 * (monitor.peer.tenant-id) whose URL ends with /peer/heartbeat. Its URL gives the address of the
 * other server, its Authorization header the PEER token. With no such monitor, or more than one,
 * this server works alone, as without a peer.
 *
 * <ul>
 *   <li>Ask a friend: before a DOWN alert, the other server checks the monitor once. It sees the
 *       monitor UP: no alert, the problem is on this side. It does not answer: alert, as always.
 *   <li>Watch each other: the peer monitor itself, with the normal checks and alerts.
 *   <li>Cannot alert: when this server cannot send alerts, it tells the other server to check its
 *       heartbeat now. The heartbeat then says cannot-alert and the other server alerts.
 * </ul>
 */
@Slf4j
@Service
public class PeerService {

  static final String HEARTBEAT_PATH = "/peer/heartbeat";

  /** Not OK: the other server is not asked before DOWN alerts. */
  public enum SetupState {
    OFF,
    OK,
    NO_PEER_MONITOR,
    MULTIPLE_PEER_MONITORS,
    NO_TOKEN
  }

  /**
   * @param monitor the peer monitor; null unless there is exactly one
   */
  public record PeerSetup(SetupState state, Monitor monitor) {

    public String baseUrl() {
      return PeerService.baseUrl(monitor.getUrl());
    }

    public String authorization() {
      return PeerService.authorization(monitor);
    }
  }

  private final PeerConfig peerConfig;
  private final MonitorRepository monitorRepository;
  private final AlertService alertService;
  private final MonitorStatusService monitorStatusService;
  private final PeerClient peerClient;
  private final Executor executor;

  public PeerService(
      PeerConfig peerConfig,
      MonitorRepository monitorRepository,
      AlertService alertService,
      MonitorStatusService monitorStatusService,
      PeerClient peerClient,
      @Qualifier("alertExecutor") Executor executor) {
    this.peerConfig = peerConfig;
    this.monitorRepository = monitorRepository;
    this.alertService = alertService;
    this.monitorStatusService = monitorStatusService;
    this.peerClient = peerClient;
    this.executor = executor;
  }

  public PeerSetup setup() {
    if (!peerConfig.isEnabled()) {
      return new PeerSetup(SetupState.OFF, null);
    }
    List<Monitor> peerMonitors =
        monitorRepository.findByTenantId(peerConfig.getTenantId()).stream()
            .filter(m -> m.getState() != MonitorState.INACTIVE)
            .filter(m -> isHeartbeatUrl(m.getUrl()))
            .toList();
    if (peerMonitors.isEmpty()) {
      return new PeerSetup(SetupState.NO_PEER_MONITOR, null);
    }
    if (peerMonitors.size() > 1) {
      return new PeerSetup(SetupState.MULTIPLE_PEER_MONITORS, null);
    }
    Monitor monitor = peerMonitors.getFirst();
    if (authorization(monitor) == null) {
      return new PeerSetup(SetupState.NO_TOKEN, monitor);
    }
    return new PeerSetup(SetupState.OK, monitor);
  }

  /** A monitor of the heartbeat of the other server, in the peer tenant. */
  public boolean isPeerMonitor(Monitor monitor) {
    return peerConfig.isPeerTenant(monitor.getTenantId()) && isHeartbeatUrl(monitor.getUrl());
  }

  /**
   * Asks the other server before a DOWN alert. Runs on the alert thread, without a transaction: the
   * other server can take seconds.
   *
   * @return true if the DOWN alert must not be sent, because the other server sees the monitor UP
   */
  public boolean vetoDownAlert(Monitor monitor, LocalDateTime outageStart) {
    if (!peerConfig.isEnabled() || isPeerMonitor(monitor)) {
      // Asking the other server whether the other server is up makes no sense
      return false;
    }
    if (!alertService.isDownAlertDue(monitor, outageStart)) {
      return false; // no contact, or the alert was sent already: nothing to confirm
    }
    PeerSetup setup = setup();
    if (setup.state() != SetupState.OK) {
      return false;
    }
    PeerResult result = ask(setup, monitor);
    monitorStatusService.setPeerResult(monitor.getId(), result);
    if (result == PeerResult.PEER_UP) {
      log.info(
          "Monitor {} ({}) is DOWN here but UP for the other server: no DOWN alert",
          monitor.getId(),
          monitor.getName());
      return true;
    }
    return false;
  }

  private PeerResult ask(PeerSetup setup, Monitor monitor) {
    try {
      PeerCheckResponseDto response =
          peerClient.check(setup.baseUrl(), setup.authorization(), checkRequest(monitor));
      log.debug(
          "Other server checked monitor {}: up={} status={} error={}",
          monitor.getId(),
          response.up(),
          response.statusCode(),
          response.errorMessage());
      return response.up() ? PeerResult.PEER_UP : PeerResult.CONFIRMED;
    } catch (RuntimeException e) {
      log.warn(
          "Other server did not check monitor {}, the DOWN alert is sent: {}",
          monitor.getId(),
          e.getMessage());
      return PeerResult.NO_ANSWER;
    }
  }

  static PeerCheckRequestDto checkRequest(Monitor monitor) {
    return new PeerCheckRequestDto(
        monitor.getUrl(),
        monitor.getHttpHeaders(),
        monitor.getStatusCodeRegex(),
        monitor.getResponseBodyRegex(),
        monitor.getPrometheusKey(),
        monitor.getPrometheusMinValue(),
        monitor.getPrometheusMaxValue(),
        monitor.getConnectTimeout(),
        monitor.getUseSystemDns());
  }

  /** After the commit of the health change: tell the other server to check the heartbeat now. */
  @TransactionalEventListener(fallbackExecution = true)
  public void onDeliveryHealthChanged(AlertDeliveryHealthChanged event) {
    try {
      executor.execute(() -> sendDistress(event));
    } catch (RejectedExecutionException e) {
      log.warn(
          "Alert queue is full, the other server is not told now: it sees it at the heartbeat");
    }
  }

  private void sendDistress(AlertDeliveryHealthChanged event) {
    PeerSetup setup = setup();
    if (setup.state() != SetupState.OK) {
      return;
    }
    String status = event.canAlert() ? PeerHeartbeatDto.OK : PeerHeartbeatDto.CANNOT_ALERT;
    try {
      peerClient.distress(
          setup.baseUrl(), setup.authorization(), new PeerDistressDto(status, null));
      log.info("Told the other server: {}", status);
    } catch (RuntimeException e) {
      // The heartbeat tells it too, at the next check
      log.warn("Could not tell the other server {}: {}", status, e.getMessage());
    }
  }

  static boolean isHeartbeatUrl(String url) {
    String path = path(url);
    return path != null && path.endsWith(HEARTBEAT_PATH);
  }

  /** https://b.example.com/api/peer/heartbeat gives https://b.example.com/api/peer. */
  static String baseUrl(String heartbeatUrl) {
    URI uri = URI.create(heartbeatUrl.trim());
    String path = stripTrailingSlash(uri.getRawPath());
    String base = path.substring(0, path.length() - "/heartbeat".length());
    return uri.getScheme() + "://" + uri.getRawAuthority() + base;
  }

  static String authorization(Monitor monitor) {
    Map<String, String> headers = monitor.getHttpHeaders();
    if (headers == null) {
      return null;
    }
    return headers.entrySet().stream()
        .filter(e -> "Authorization".equalsIgnoreCase(e.getKey()))
        .map(Map.Entry::getValue)
        .filter(v -> v != null && !v.isBlank())
        .findFirst()
        .orElse(null);
  }

  private static String path(String url) {
    if (url == null) {
      return null;
    }
    try {
      String path = URI.create(url.trim()).getRawPath();
      return path != null ? stripTrailingSlash(path) : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String stripTrailingSlash(String path) {
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }
}
