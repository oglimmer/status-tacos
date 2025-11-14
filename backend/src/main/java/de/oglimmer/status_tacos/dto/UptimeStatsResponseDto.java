/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import de.oglimmer.status_tacos.persistence.UptimeStats;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UptimeStatsResponseDto {

  private Long id;
  private Integer monitorId;
  private String monitorName;
  private Integer tenantId;
  private TenantResponseDto tenant;
  private UptimeStats.PeriodType periodType;
  private LocalDateTime periodStart;
  private LocalDateTime periodEnd;
  private Integer totalChecks;
  private Integer successfulChecks;
  private BigDecimal uptimePercentage;
  private Integer minResponseTimeMs;
  private Integer maxResponseTimeMs;
  private Integer avgResponseTimeMs;
  private Integer p99ResponseTimeMs;
  private String responseTimeData;
  private String statusChangeData;
  private LocalDateTime calculatedAt;
}
