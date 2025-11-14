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
public class CheckResultResponseDto {

  private Long id;
  private Integer monitorId;
  private String monitorName;
  private Integer tenantId;
  private TenantResponseDto tenant;
  private LocalDateTime checkedAt;
  private Integer statusCode;
  private Integer responseTimeMs;
  private Boolean isUp;
  private String errorMessage;
}
