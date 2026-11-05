/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import de.oglimmer.status_tacos.persistence.MonitorState;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitorResponseDto {

  private Integer id;
  private String name;
  private String url;
  private Integer tenantId;
  private TenantResponseDto tenant;
  private MonitorState state;

  /** End of the silence. Only for state SILENT. */
  private Instant silentUntil;

  /** Daily pause without checks, HH:mm in dailyPauseTimezone. Null: no daily pause. */
  private String dailyPauseFrom;

  private String dailyPauseUntil;
  private String dailyPauseTimezone;

  private Map<String, String> httpHeaders;
  private String statusCodeRegex;
  private String responseBodyRegex;
  private String prometheusKey;
  private Double prometheusMinValue;
  private Double prometheusMaxValue;
  private Integer checkInterval;
  private Integer connectTimeout;
  private Boolean useSystemDns;
  private Integer alertingThreshold;
  private Boolean showOnPublicPage;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
