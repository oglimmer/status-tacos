/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.oglimmer.status_tacos.dto.PublicMonitorStatusDto;
import de.oglimmer.status_tacos.dto.PublicStatusPageDto;
import de.oglimmer.status_tacos.dto.StatsPeriod;
import de.oglimmer.status_tacos.dto.StatusType;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorStatus;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.TenantStatusPageRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicStatusServiceTest {

  private static final LocalDateTime OUTAGE_START = LocalDateTime.of(2026, 10, 18, 9, 0);

  private static final Tenant TENANT = Tenant.builder().id(7).name("Acme").code("acme").build();

  private final TenantRepository tenantRepository = mock(TenantRepository.class);
  private final MonitorRepository monitorRepository = mock(MonitorRepository.class);
  private final UptimeStatsService uptimeStatsService = mock(UptimeStatsService.class);
  private final TenantStatusPageRepository statusPageRepository =
      mock(TenantStatusPageRepository.class);
  private final Clock clock = mock(Clock.class);
  private PublicStatusService service;

  @BeforeEach
  void setUp() {
    StatusPageRenderer renderer = new StatusPageRenderer(Clock.systemUTC());
    service =
        new PublicStatusService(
            tenantRepository,
            monitorRepository,
            statusPageRepository,
            uptimeStatsService,
            renderer,
            clock);
    when(clock.instant()).thenReturn(Instant.parse("2026-10-18T10:00:00Z"));
    when(tenantRepository.findActiveByCode("acme")).thenReturn(Optional.of(TENANT));
  }

  @Test
  void showsOnlyNameStatusDownSinceAndUptime() {
    Monitor up = monitor(1, "API", status(MonitorStatus.StatusType.up, OUTAGE_START));
    Monitor down = monitor(2, "Web", status(MonitorStatus.StatusType.down, OUTAGE_START));
    Monitor noCheck = monitor(3, "New", null);
    when(monitorRepository.findShownOnPublicPageWithStatus(7))
        .thenReturn(List.of(up, down, noCheck));
    when(uptimeStatsService.getUptimePercentages(any(), eq(StatsPeriod.NINETY_DAYS)))
        .thenReturn(Map.of(1, new BigDecimal("99.95"), 2, new BigDecimal("97.10")));

    PublicStatusPageDto page = service.getPublicStatusPage("acme").orElseThrow();

    assertThat(page.getTenantName()).isEqualTo("Acme");
    assertThat(page.getMonitors())
        .containsExactly(
            new PublicMonitorStatusDto("API", StatusType.up, null, new BigDecimal("99.95")),
            new PublicMonitorStatusDto(
                "Web", StatusType.down, OUTAGE_START, new BigDecimal("97.10")),
            new PublicMonitorStatusDto("New", null, null, null));
  }

  @Test
  void unknownTenant_isEmpty() {
    when(tenantRepository.findActiveByCode("nope")).thenReturn(Optional.empty());

    assertThat(service.getPublicStatusPage("nope")).isEmpty();
    verifyNoInteractions(monitorRepository);
  }

  @Test
  void cachesTheResult() {
    when(monitorRepository.findShownOnPublicPageWithStatus(7)).thenReturn(List.of());

    service.getPublicStatusPage("acme");
    service.getPublicStatusPage("acme");
    verify(monitorRepository, times(1)).findShownOnPublicPageWithStatus(7);

    when(clock.instant())
        .thenReturn(Instant.parse("2026-10-18T10:00:00Z").plus(PublicStatusService.CACHE_TTL));
    service.getPublicStatusPage("acme");
    verify(monitorRepository, times(2)).findShownOnPublicPageWithStatus(7);
  }

  private static Monitor monitor(int id, String name, MonitorStatus status) {
    return Monitor.builder()
        .id(id)
        .name(name)
        .url("https://secret.example")
        .monitorStatus(status)
        .build();
  }

  private static MonitorStatus status(MonitorStatus.StatusType type, LocalDateTime outageStart) {
    return MonitorStatus.builder().currentStatus(type).outageStartedAt(outageStart).build();
  }
}
