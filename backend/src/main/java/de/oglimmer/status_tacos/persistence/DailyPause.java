/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * A time window each day without checks, for example for a nightly database dump. From is
 * inclusive, until is exclusive. When until is before from, the window goes past midnight (23:30 to
 * 00:30). The times are wall-clock times in the time zone, so the window follows daylight saving
 * time.
 */
public record DailyPause(LocalTime from, LocalTime until, ZoneId zone) {

  /** The time zone when the request has times but no time zone. */
  public static final String DEFAULT_TIMEZONE = "UTC";

  /** For the bean validation of the request DTO: HH:mm, 00:00 to 23:59. */
  public static final String TIME_PATTERN = "([01]\\d|2[0-3]):[0-5]\\d";

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  /** The pause of the monitor, or null when it has none. */
  public static DailyPause of(Monitor monitor) {
    if (monitor.getDailyPauseFrom() == null || monitor.getDailyPauseUntil() == null) {
      return null;
    }
    return new DailyPause(
        monitor.getDailyPauseFrom(),
        monitor.getDailyPauseUntil(),
        zoneOrUtc(monitor.getDailyPauseTimezone()));
  }

  /** True when the monitor has a daily pause and the time is in it. */
  public static boolean isPaused(Monitor monitor, Instant now) {
    DailyPause pause = of(monitor);
    return pause != null && pause.contains(now);
  }

  public boolean contains(Instant instant) {
    LocalTime time = instant.atZone(zone).toLocalTime();
    if (from.isBefore(until)) {
      return !time.isBefore(from) && time.isBefore(until);
    }
    // Past midnight: in the evening part or in the morning part
    return !time.isBefore(from) || time.isBefore(until);
  }

  /** The length of the window on a normal day (no daylight saving change). */
  public Duration length() {
    Duration d = Duration.between(from, until);
    return d.isNegative() ? d.plusDays(1) : d;
  }

  /** "HH:mm", or null for null. */
  public static String format(LocalTime time) {
    return time == null ? null : time.format(HH_MM);
  }

  /** "HH:mm" to a time. Null or blank gives null. Other text throws IllegalArgumentException. */
  public static LocalTime parseTime(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return LocalTime.parse(text, HH_MM);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Time must be HH:mm: " + text, e);
    }
  }

  /** True for an IANA time zone id like Europe/Berlin or UTC. */
  public static boolean isValidZone(String zone) {
    try {
      ZoneId.of(zone);
      return true;
    } catch (DateTimeException | NullPointerException e) {
      return false;
    }
  }

  private static ZoneId zoneOrUtc(String zone) {
    return zone != null && isValidZone(zone) ? ZoneId.of(zone) : ZoneId.of(DEFAULT_TIMEZONE);
  }
}
