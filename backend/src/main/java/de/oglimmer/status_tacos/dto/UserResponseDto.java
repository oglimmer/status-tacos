/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

  private Integer id;
  private String email;
  private String firstName;
  private String lastName;
  private Integer[] tenantIds;
  private List<TenantResponseDto> tenants;
  private Boolean isActive;

  /** Maximum number of monitors in all tenants of the user. Null: no limit. */
  private Integer maxMonitors;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
