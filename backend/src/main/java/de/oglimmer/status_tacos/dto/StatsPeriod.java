/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Periods of the uptime stats. 90 days is the maximum.
 *
 * <p>A window is half-open: [start, now). The start is aligned to a full UTC hour (7 days) or a
 * full UTC day (90 days), so the chart buckets do not move between requests. The window is up to
 * one hour or one day longer than its nominal length.
 */
@Getter
@AllArgsConstructor
public enum StatsPeriod {
  SEVEN_DAYS(7, ChronoUnit.HOURS, 60),
  NINETY_DAYS(90, ChronoUnit.DAYS, 360);

  private final int days;
  private final ChronoUnit alignment;
  private final int chartIntervalMinutes;

  public LocalDateTime windowStart(LocalDateTime now) {
    return now.truncatedTo(alignment).minusDays(days);
  }

  /** Parses the path value of the API, for example "seven_days". */
  public static Optional<StatsPeriod> fromPath(String value) {
    return Arrays.stream(values()).filter(p -> p.name().equalsIgnoreCase(value)).findFirst();
  }
}
