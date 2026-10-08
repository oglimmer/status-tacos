/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Data;

/** The iOS push alert of the current user for one tenant. */
@Data
public class PushAlertRequestDto {

  @JsonProperty("isActive")
  private boolean isActive = true;

  /** true: alerts for every monitor of the tenant. false: only for monitorIds. */
  private boolean allMonitors = true;

  private Set<Integer> monitorIds;
}
