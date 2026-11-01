/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.oglimmer.status_tacos.config.PeerConfig;
import de.oglimmer.status_tacos.dto.PeerCheckResponseDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.PeerResult;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class PeerServiceTest {

  private static final int PEER_TENANT = 7;
  private static final LocalDateTime OUTAGE = LocalDateTime.of(2026, 11, 1, 10, 0);

  @Mock private MonitorRepository monitorRepository;
  @Mock private AlertService alertService;
  @Mock private MonitorStatusService monitorStatusService;
  @Mock private PeerClient peerClient;

  private PeerConfig peerConfig;
  private PeerService peerService;
  private Monitor peerMonitor;
  private Monitor shop;

  @BeforeEach
  void setUp() {
    peerConfig = new PeerConfig();
    peerConfig.setTenantId(PEER_TENANT);
    peerService =
        new PeerService(
            peerConfig,
            monitorRepository,
            alertService,
            monitorStatusService,
            peerClient,
            Runnable::run);
    peerMonitor =
        Monitor.builder()
            .id(1)
            .tenantId(PEER_TENANT)
            .name("Server B")
            .url("https://b.example.com/api/peer/heartbeat")
            .httpHeaders(Map.of("authorization", "Bearer stt_peer"))
            .build();
    shop = Monitor.builder().id(2).tenantId(3).name("Shop").url("https://shop.example.com").build();
    lenient().when(monitorRepository.findByTenantId(PEER_TENANT)).thenReturn(List.of(peerMonitor));
    lenient().when(alertService.isDownAlertDue(any(), any())).thenReturn(true);
  }

  @Test
  void peerSeesDown_alertIsSent() {
    when(peerClient.check(eq("https://b.example.com/api/peer"), eq("Bearer stt_peer"), any()))
        .thenReturn(new PeerCheckResponseDto(false, 503, 40, null));

    assertThat(peerService.vetoDownAlert(shop, OUTAGE)).isFalse();
    verify(monitorStatusService).setPeerResult(2, PeerResult.CONFIRMED);
  }

  @Test
  void peerSeesUp_noAlert() {
    when(peerClient.check(anyString(), anyString(), any()))
        .thenReturn(new PeerCheckResponseDto(true, 200, 40, null));

    assertThat(peerService.vetoDownAlert(shop, OUTAGE)).isTrue();
    verify(monitorStatusService).setPeerResult(2, PeerResult.PEER_UP);
  }

  @Test
  void peerDoesNotAnswer_alertIsSent() {
    when(peerClient.check(anyString(), anyString(), any()))
        .thenThrow(new ResourceAccessException("Read timed out"));

    assertThat(peerService.vetoDownAlert(shop, OUTAGE)).isFalse();
    verify(monitorStatusService).setPeerResult(2, PeerResult.NO_ANSWER);
  }

  @Test
  void thePeerMonitorItself_isNotConfirmedByThePeer() {
    assertThat(peerService.vetoDownAlert(peerMonitor, OUTAGE)).isFalse();
    verifyNoInteractions(peerClient);
  }

  @Test
  void alertAlreadySent_peerIsNotAsked() {
    when(alertService.isDownAlertDue(shop, OUTAGE)).thenReturn(false);

    assertThat(peerService.vetoDownAlert(shop, OUTAGE)).isFalse();
    verifyNoInteractions(peerClient);
  }

  @Test
  void peeringOff_peerIsNotAsked() {
    peerConfig.setTenantId(null);

    assertThat(peerService.vetoDownAlert(shop, OUTAGE)).isFalse();
    assertThat(peerService.setup().state()).isEqualTo(PeerService.SetupState.OFF);
    verifyNoInteractions(peerClient);
  }

  @Test
  void twoPeerMonitors_setupNotValid_peerIsNotAsked() {
    Monitor second =
        Monitor.builder().id(9).tenantId(PEER_TENANT).url(peerMonitor.getUrl()).build();
    when(monitorRepository.findByTenantId(PEER_TENANT)).thenReturn(List.of(peerMonitor, second));

    assertThat(peerService.setup().state())
        .isEqualTo(PeerService.SetupState.MULTIPLE_PEER_MONITORS);
    assertThat(peerService.vetoDownAlert(shop, OUTAGE)).isFalse();
    verifyNoInteractions(peerClient);
  }

  @Test
  void setupStates() {
    when(monitorRepository.findByTenantId(PEER_TENANT)).thenReturn(List.of());
    assertThat(peerService.setup().state()).isEqualTo(PeerService.SetupState.NO_PEER_MONITOR);

    peerMonitor.setHttpHeaders(Map.of());
    when(monitorRepository.findByTenantId(PEER_TENANT)).thenReturn(List.of(peerMonitor));
    assertThat(peerService.setup().state()).isEqualTo(PeerService.SetupState.NO_TOKEN);

    peerMonitor.setState(MonitorState.INACTIVE);
    assertThat(peerService.setup().state()).isEqualTo(PeerService.SetupState.NO_PEER_MONITOR);
  }

  @Test
  void urlHelpers() {
    assertThat(PeerService.isHeartbeatUrl("https://b.example.com/api/peer/heartbeat/")).isTrue();
    assertThat(PeerService.isHeartbeatUrl("https://b.example.com/api/health")).isFalse();
    assertThat(PeerService.isHeartbeatUrl("not a url")).isFalse();
    assertThat(PeerService.baseUrl("https://b.example.com:8443/api/peer/heartbeat?x=1"))
        .isEqualTo("https://b.example.com:8443/api/peer");
  }

  @Test
  void cannotAlert_tellsThePeer() {
    peerService.onDeliveryHealthChanged(new AlertDeliveryHealthChanged(false));

    verify(peerClient)
        .distress(
            eq("https://b.example.com/api/peer"),
            eq("Bearer stt_peer"),
            argThat(d -> "cannot-alert".equals(d.status())));
  }
}
