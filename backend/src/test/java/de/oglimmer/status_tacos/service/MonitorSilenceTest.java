/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.oglimmer.status_tacos.dto.MonitorRequestDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.SilenceDuration;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MonitorSilenceTest {

  private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
  private static final int TENANT = 1;

  private MonitorRepository repository;
  private MonitorService service;
  private Monitor monitor;

  @BeforeEach
  void setUp() {
    repository = mock(MonitorRepository.class);
    service = new MonitorService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    monitor =
        Monitor.builder().id(7).name("Shop").url("https://shop.example").tenantId(TENANT).build();
    when(repository.findByIdAndTenantId(7, TENANT)).thenReturn(Optional.of(monitor));
    when(repository.save(any(Monitor.class))).thenAnswer(i -> i.getArgument(0));
  }

  @ParameterizedTest
  @EnumSource(SilenceDuration.class)
  void silenceEndsAfterTheChosenDuration(SilenceDuration duration) {
    Monitor result = service.updateMonitorState(TENANT, 7, MonitorState.SILENT, duration);

    assertThat(result.getState()).isEqualTo(MonitorState.SILENT);
    assertThat(result.getSilentUntil()).isEqualTo(NOW.plus(duration.getDuration()));
  }

  @Test
  void silenceWithoutDurationGets24Hours() {
    Monitor result = service.updateMonitorState(TENANT, 7, MonitorState.SILENT);

    assertThat(result.getSilentUntil()).isEqualTo(NOW.plus(Duration.ofHours(24)));
  }

  @Test
  void runningSilenceKeepsItsEndWhenNoDurationIsSent() {
    Instant end = NOW.plus(Duration.ofHours(2));
    monitor.setState(MonitorState.SILENT);
    monitor.setSilentUntil(end);

    MonitorRequestDto edit =
        MonitorRequestDto.builder()
            .name("Shop")
            .url("https://shop.example")
            .tenantId(TENANT)
            .state(MonitorState.SILENT)
            .build();
    Monitor result = service.updateMonitor(TENANT, 7, edit);

    assertThat(result.getSilentUntil()).isEqualTo(end);
  }

  @Test
  void otherStatesHaveNoSilenceEnd() {
    monitor.setState(MonitorState.SILENT);
    monitor.setSilentUntil(NOW.plus(Duration.ofHours(2)));

    Monitor result = service.updateMonitorState(TENANT, 7, MonitorState.ACTIVE);

    assertThat(result.getSilentUntil()).isNull();
  }

  @Test
  void createWithSilentForSetsTheEnd() {
    MonitorRequestDto create =
        MonitorRequestDto.builder()
            .name("Shop")
            .url("https://shop.example")
            .tenantId(TENANT)
            .state(MonitorState.SILENT)
            .silentFor("3d")
            .build();
    when(repository.findByTenantIdAndUrlIgnoreCase(TENANT, "https://shop.example"))
        .thenReturn(Optional.empty());

    Monitor result = service.createMonitor(TENANT, create);

    assertThat(result.getSilentUntil()).isEqualTo(NOW.plus(Duration.ofDays(3)));
  }

  @Test
  void onlyTheFourDurationsAreAllowed() {
    assertThat(SilenceDuration.fromCode("7d")).isEqualTo(SilenceDuration.SEVEN_DAYS);
    assertThatThrownBy(() -> SilenceDuration.fromCode("2h"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void expiredSilencesEndAtNow() {
    when(repository.endExpiredSilences(any(), any())).thenReturn(2);

    assertThat(service.endExpiredSilences()).isEqualTo(2);
    verify(repository).endExpiredSilences(eq(NOW), any());
  }
}
