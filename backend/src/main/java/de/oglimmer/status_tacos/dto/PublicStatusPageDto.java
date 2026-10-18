/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The public status page of one tenant. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicStatusPageDto {

  private String tenantName;
  private List<PublicMonitorStatusDto> monitors;
}
