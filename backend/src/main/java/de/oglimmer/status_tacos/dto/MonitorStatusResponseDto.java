/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitorStatusResponseDto {

  private Integer monitorId;
  private String monitorName;
  private String monitorUrl;
  private Integer tenantId;
  private TenantResponseDto tenant;
  private StatusType currentStatus;
  private LocalDateTime lastCheckedAt;
  private LocalDateTime lastUpAt;
  private LocalDateTime lastDownAt;
  private Integer consecutiveFailures;
  private Integer lastResponseTimeMs;
  private Integer lastStatusCode;
  private LocalDateTime updatedAt;
}
