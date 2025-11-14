/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.validation.MultipleOf15;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitorRequestDto {

  @NotBlank(message = "Name is required")
  @Size(max = 255, message = "Name must not exceed 255 characters")
  private String name;

  @NotBlank(message = "URL is required")
  @Size(max = 2048, message = "URL must not exceed 2048 characters")
  private String url;

  @NotNull(message = "Tenant ID is required")
  private Integer tenantId;

  @Builder.Default private MonitorState state = MonitorState.ACTIVE;

  private Map<String, String> httpHeaders;

  @Size(max = 500, message = "Status code regex must not exceed 500 characters")
  private String statusCodeRegex;

  @Size(max = 1000, message = "Response body regex must not exceed 1000 characters")
  private String responseBodyRegex;

  @Size(max = 255, message = "Prometheus key must not exceed 255 characters")
  private String prometheusKey;

  private Double prometheusMinValue;

  private Double prometheusMaxValue;

  @Min(value = 15, message = "Alerting threshold must be at least 15 seconds")
  @MultipleOf15(message = "Alerting threshold must be a multiple of 15 seconds")
  @Builder.Default
  private Integer alertingThreshold = 30;
}
