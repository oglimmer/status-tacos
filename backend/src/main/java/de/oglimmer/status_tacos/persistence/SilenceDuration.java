/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import java.time.Duration;
import java.util.Arrays;

/** How long a SILENT monitor sends no alerts. After that time the monitor is ACTIVE again. */
public enum SilenceDuration {
  ONE_HOUR("1h", Duration.ofHours(1)),
  ONE_DAY("24h", Duration.ofHours(24)),
  THREE_DAYS("3d", Duration.ofDays(3)),
  SEVEN_DAYS("7d", Duration.ofDays(7));

  /** For clients that make a monitor SILENT and do not send a duration. */
  public static final SilenceDuration DEFAULT = ONE_DAY;

  /** For the bean validation of the request DTO. Keep it the same as the codes. */
  public static final String CODE_PATTERN = "1h|24h|3d|7d";

  private final String code;
  private final Duration duration;

  SilenceDuration(String code, Duration duration) {
    this.code = code;
    this.duration = duration;
  }

  public String getCode() {
    return code;
  }

  public Duration getDuration() {
    return duration;
  }

  /** "1h", "24h", "3d" or "7d". Null for null. Other values throw IllegalArgumentException. */
  public static SilenceDuration fromCode(String code) {
    if (code == null) {
      return null;
    }
    return Arrays.stream(values())
        .filter(d -> d.code.equals(code))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Silence duration must be one of 1h, 24h, 3d, 7d: " + code));
  }
}
