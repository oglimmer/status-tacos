/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uptime stats of one monitor for the window [periodStart, periodEnd). Response times count
 * successful checks only. uptimePercentage is rounded down and null when there are no checks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UptimeStatsResponseDto {

  private Integer monitorId;
  private String monitorName;
  private StatsPeriod periodType;
  private LocalDateTime periodStart;
  private LocalDateTime periodEnd;
  private Integer intervalMinutes;
  private Long totalChecks;
  private Long successfulChecks;
  private BigDecimal uptimePercentage;
  private Integer minResponseTimeMs;
  private Integer maxResponseTimeMs;
  private Integer avgResponseTimeMs;
  private Integer p99ResponseTimeMs;
  private List<ResponseTimeDataPointDto> responseTimeDataPoints;
  private List<StatusDownPeriodsDto> statusDownPeriods;
}
