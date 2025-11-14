/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseTimeHistoryResponseDto {

  private Integer monitorId;
  private String monitorName;
  private Integer intervalMinutes;
  private Integer totalDataPoints;
  private Double uptimePercentage24h;
  private Integer totalChecks24h;
  private Integer successfulChecks24h;
  private List<ResponseTimeDataPointDto> dataPoints;
  private List<StatusDownPeriodsDto> statusDownPeriods;
}
