/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.oglimmer.status_tacos.persistence.DailyPause;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.SilenceDuration;
import de.oglimmer.status_tacos.validation.HttpUrl;
import de.oglimmer.status_tacos.validation.MultipleOf15;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
  @HttpUrl
  @Size(max = 2048, message = "URL must not exceed 2048 characters")
  private String url;

  @NotNull(message = "Tenant ID is required")
  private Integer tenantId;

  @Builder.Default private MonitorState state = MonitorState.ACTIVE;

  /**
   * Only for state SILENT: 1h, 24h, 3d or 7d. Empty: a running silence keeps its end, a new silence
   * gets 24h.
   */
  @Pattern(
      regexp = SilenceDuration.CODE_PATTERN,
      message = "Silence duration must be one of 1h, 24h, 3d, 7d")
  private String silentFor;

  /**
   * Daily pause: no checks and no alerts from dailyPauseFrom to dailyPauseUntil each day (HH:mm).
   * Until before from: past midnight. Both empty: no daily pause.
   */
  @Pattern(regexp = DailyPause.TIME_PATTERN, message = "Daily pause start must be HH:mm")
  private String dailyPauseFrom;

  @Pattern(regexp = DailyPause.TIME_PATTERN, message = "Daily pause end must be HH:mm")
  private String dailyPauseUntil;

  /** IANA time zone of the daily pause times, for example Europe/Berlin. Empty: UTC. */
  @Size(max = 64, message = "Daily pause time zone must not exceed 64 characters")
  private String dailyPauseTimezone;

  @JsonIgnore
  @AssertTrue(message = "Daily pause needs a start and an end, and they must not be the same")
  public boolean isDailyPauseComplete() {
    boolean hasFrom = dailyPauseFrom != null && !dailyPauseFrom.isBlank();
    boolean hasUntil = dailyPauseUntil != null && !dailyPauseUntil.isBlank();
    if (hasFrom != hasUntil) {
      return false;
    }
    return !hasFrom || !dailyPauseFrom.equals(dailyPauseUntil);
  }

  @JsonIgnore
  @AssertTrue(message = "Daily pause time zone is not a known time zone, for example Europe/Berlin")
  public boolean isDailyPauseTimezoneValid() {
    return dailyPauseTimezone == null
        || dailyPauseTimezone.isBlank()
        || DailyPause.isValidZone(dailyPauseTimezone);
  }

  private Map<String, String> httpHeaders;

  @Size(max = 500, message = "Status code regex must not exceed 500 characters")
  private String statusCodeRegex;

  @Size(max = 1000, message = "Response body regex must not exceed 1000 characters")
  private String responseBodyRegex;

  @Size(max = 255, message = "Prometheus key must not exceed 255 characters")
  private String prometheusKey;

  private Double prometheusMinValue;

  private Double prometheusMaxValue;

  @Min(value = 15, message = "Check interval must be at least 15 seconds")
  @Max(value = 3600, message = "Check interval must not exceed 3600 seconds")
  @MultipleOf15(message = "Check interval must be a multiple of 15 seconds")
  @Builder.Default
  private Integer checkInterval = 15;

  @Min(value = 1, message = "Connect timeout must be at least 1 second")
  @Max(value = 10, message = "Connect timeout must not exceed 10 seconds")
  @Builder.Default
  private Integer connectTimeout = 5;

  @Builder.Default private Boolean useSystemDns = false;

  @Min(value = 15, message = "Alerting threshold must be at least 15 seconds")
  @MultipleOf15(message = "Alerting threshold must be a multiple of 15 seconds")
  @Builder.Default
  private Integer alertingThreshold = 30;

  @Builder.Default private Boolean showOnPublicPage = false;
}
