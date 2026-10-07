/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.oglimmer.status_tacos.dto.StatsPeriod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
}
