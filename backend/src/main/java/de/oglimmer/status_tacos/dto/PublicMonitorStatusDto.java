/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One monitor on the public status page. It has no sign-in, so this holds only what the page shows:
 * no ID, URL, tenant or check details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicMonitorStatusDto {

  private String name;

  /** Null when the monitor has no check yet. */
  private StatusType currentStatus;

  /** First failed check of the current outage. Null when the monitor is up. */
  private LocalDateTime downSince;

  /** Rounded down. Null when there are no checks in the 90 days. */
  private BigDecimal uptimePercentage90d;
}
