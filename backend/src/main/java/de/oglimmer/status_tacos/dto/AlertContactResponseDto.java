/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.oglimmer.status_tacos.persistence.AlertContact;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertContactResponseDto {

  private Integer id;
  private AlertContact.AlertContactType type;
  private String value;
  private String name;

  @JsonProperty("isActive")
  private boolean isActive;

  private TenantResponseDto tenant;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // HTTP-specific fields
  private String httpMethod;
  private Map<String, String> httpHeaders;
  private String httpBody;
  private String httpContentType;
}
