/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import de.oglimmer.status_tacos.persistence.MonitorEnvironment;
import de.oglimmer.status_tacos.persistence.MonitorState;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertResponseDto {

  private Integer monitorId;
  private String monitorName;
  private String tenantName;
  private String status;
  private LocalDateTime downtimeStart;

  /** For the filters of the macOS widget. Null when not set. */
  private MonitorEnvironment environment;

  private MonitorState state;
}
