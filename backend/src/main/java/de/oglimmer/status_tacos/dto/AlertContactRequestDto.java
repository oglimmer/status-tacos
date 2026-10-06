/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import de.oglimmer.status_tacos.persistence.AlertContact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class AlertContactRequestDto {

  @NotNull private AlertContact.AlertContactType type;

  @NotBlank
  @Size(max = 2048)
  private String value;

  @Size(max = 100)
  private String name;

  private boolean isActive = true;

  // HTTP-specific fields
  private String httpMethod;
  private Map<String, String> httpHeaders;
  private String httpBody;
  private String httpContentType;

  // Scope: all monitors of the tenant, or only the listed monitors
  private boolean allMonitors = true;
  private Set<Integer> monitorIds;

  public boolean isValidEmail() {
    return value != null && value.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
  }

  public boolean isValidUrl() {
    return value != null && value.matches("^https?://.*");
  }

  public boolean isValidHttpsUrl() {
    return value != null && value.matches("^https://.*");
  }
}
