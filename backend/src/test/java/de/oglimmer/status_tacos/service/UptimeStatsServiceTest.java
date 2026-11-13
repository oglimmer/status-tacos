/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.oglimmer.status_tacos.dto.ResponseTimeHistoryResponseDto;
import de.oglimmer.status_tacos.dto.StatsPeriod;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.repository.CheckPoint;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import de.oglimmer.status_tacos.repository.CheckRollupRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UptimeStatsServiceTest {

  @Test
  void uptime_isRoundedDown() {
    assertThat(UptimeStatsService.uptimePercentage(99_996, 100_000))
        .isEqualTo(new BigDecimal("99.99"));
    assertThat(UptimeStatsService.uptimePercentage(2, 3)).isEqualTo(new BigDecimal("66.66"));
    assertThat(UptimeStatsService.uptimePercentage(10, 10)).isEqualTo(new BigDecimal("100.00"));
    assertThat(UptimeStatsService.uptimePercentage(0, 10)).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void uptime_withoutChecks_isNull() {
    assertThat(UptimeStatsService.uptimePercentage(0, 0)).isNull();
  }

  @Test
  void windows_areAlignedToUtcHoursAndDays() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 7, 14, 37, 12);

    assertThat(StatsPeriod.SEVEN_DAYS.windowStart(now))
        .isEqualTo(LocalDateTime.of(2026, 9, 30, 14, 0));
    assertThat(StatsPeriod.NINETY_DAYS.windowStart(now))
        .isEqualTo(LocalDateTime.of(2026, 7, 9, 0, 0));
  }

  @Test
  void period_fromPath() {
    assertThat(StatsPeriod.fromPath("seven_days")).contains(StatsPeriod.SEVEN_DAYS);
    assertThat(StatsPeriod.fromPath("NINETY_DAYS")).contains(StatsPeriod.NINETY_DAYS);
    assertThat(StatsPeriod.fromPath("three_sixty_five_days")).isEmpty();
  }

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 30, 30);

  @Test
  void history24h_hasResponseTimeSummary_ofSuccessfulChecksOnly() {
    List<CheckPoint> checks = new ArrayList<>();
    // 100 successful checks with 1..100 ms, plus failed checks with a high response time
    for (int i = 1; i <= 100; i++) {
      checks.add(new CheckPoint(1, NOW.minusMinutes(i), true, i, 60));
    }
    checks.add(new CheckPoint(1, NOW.minusMinutes(101), false, 5000, 60));
    checks.add(new CheckPoint(1, NOW.minusMinutes(102), false, null, 60));
    // A successful check without a response time does not count
    checks.add(new CheckPoint(1, NOW.minusMinutes(103), true, null, 60));

    ResponseTimeHistoryResponseDto history = history24h(checks);

    assertThat(history.getMinResponseTimeMs()).isEqualTo(1);
    assertThat(history.getMaxResponseTimeMs()).isEqualTo(100);
    // (1 + ... + 100) / 100 = 50.5, rounded like the 7-day stats
    assertThat(history.getAvgResponseTimeMs()).isEqualTo(51);
    // Exact p99 is 99; the histogram gives the middle of its bucket, at most 2.5 % off
    assertThat(history.getP99ResponseTimeMs()).isBetween(97, 100);
    assertThat(history.getSuccessfulChecks24h()).isEqualTo(101);
    assertThat(history.getTotalChecks24h()).isEqualTo(103);
  }

  @Test
  void history24h_oneCheck_allValuesAreThatCheck() {
    ResponseTimeHistoryResponseDto history =
        history24h(List.of(new CheckPoint(1, NOW.minusMinutes(1), true, 123, 60)));

    assertThat(history.getMinResponseTimeMs()).isEqualTo(123);
    assertThat(history.getMaxResponseTimeMs()).isEqualTo(123);
    assertThat(history.getAvgResponseTimeMs()).isEqualTo(123);
    // Clamped to min and max
    assertThat(history.getP99ResponseTimeMs()).isEqualTo(123);
  }

  @Test
  void history24h_withoutSuccessfulChecks_hasNoResponseTimes() {
    ResponseTimeHistoryResponseDto onlyDown =
        history24h(List.of(new CheckPoint(1, NOW.minusMinutes(1), false, 900, 60)));
    ResponseTimeHistoryResponseDto noChecks = history24h(List.of());

    for (ResponseTimeHistoryResponseDto history : List.of(onlyDown, noChecks)) {
      assertThat(history.getMinResponseTimeMs()).isNull();
      assertThat(history.getMaxResponseTimeMs()).isNull();
      assertThat(history.getAvgResponseTimeMs()).isNull();
      assertThat(history.getP99ResponseTimeMs()).isNull();
    }
  }

  private static ResponseTimeHistoryResponseDto history24h(List<CheckPoint> checks) {
    CheckRollupRepository rollups = mock(CheckRollupRepository.class);
    CheckResultRepository results = mock(CheckResultRepository.class);
    MonitorRepository monitors = mock(MonitorRepository.class);
    Monitor monitor = Monitor.builder().id(1).name("M").build();
    when(monitors.findByIdAndTenantIdIn(1, Set.of(7))).thenReturn(Optional.of(monitor));
    when(results.findCheckPoints(anyCollection(), any(), any())).thenReturn(checks);
    Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    return new UptimeStatsService(rollups, results, monitors, clock)
        .getResponseTimeHistory24h(Set.of(7), 1)
        .orElseThrow();
  }
}
