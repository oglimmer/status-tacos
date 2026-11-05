/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DailyPauseTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  @Test
  void windowOnOneDay_startIsInside_endIsOutside() {
    DailyPause pause = new DailyPause(LocalTime.of(2, 0), LocalTime.of(2, 30), ZoneId.of("UTC"));

    assertThat(pause.contains(Instant.parse("2026-11-05T01:59:59Z"))).isFalse();
    assertThat(pause.contains(Instant.parse("2026-11-05T02:00:00Z"))).isTrue();
    assertThat(pause.contains(Instant.parse("2026-11-05T02:29:59Z"))).isTrue();
    assertThat(pause.contains(Instant.parse("2026-11-05T02:30:00Z"))).isFalse();
  }

  @Test
  void windowPastMidnight() {
    DailyPause pause = new DailyPause(LocalTime.of(23, 30), LocalTime.of(0, 30), ZoneId.of("UTC"));

    assertThat(pause.contains(Instant.parse("2026-11-05T23:45:00Z"))).isTrue();
    assertThat(pause.contains(Instant.parse("2026-11-05T00:15:00Z"))).isTrue();
    assertThat(pause.contains(Instant.parse("2026-11-05T12:00:00Z"))).isFalse();
    assertThat(pause.length()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void timesAreWallClockTimesOfTheZone_alsoInSummer() {
    DailyPause pause = new DailyPause(LocalTime.of(2, 0), LocalTime.of(2, 30), BERLIN);

    // Winter: UTC+1
    assertThat(pause.contains(Instant.parse("2026-11-05T01:10:00Z"))).isTrue();
    assertThat(pause.contains(Instant.parse("2026-11-05T02:10:00Z"))).isFalse();
    // Summer: UTC+2
    assertThat(pause.contains(Instant.parse("2026-07-05T00:10:00Z"))).isTrue();
    assertThat(pause.contains(Instant.parse("2026-07-05T01:10:00Z"))).isFalse();
  }

  @Test
  void monitorWithoutPauseIsNeverPaused() {
    Monitor monitor = Monitor.builder().id(1).build();

    assertThat(DailyPause.of(monitor)).isNull();
    assertThat(DailyPause.isPaused(monitor, Instant.now())).isFalse();
  }

  @Test
  void parseAndFormat() {
    assertThat(DailyPause.parseTime("02:05")).isEqualTo(LocalTime.of(2, 5));
    assertThat(DailyPause.parseTime(" ")).isNull();
    assertThat(DailyPause.format(LocalTime.of(2, 5))).isEqualTo("02:05");
    assertThatThrownBy(() -> DailyPause.parseTime("2am"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(DailyPause.isValidZone("Europe/Berlin")).isTrue();
    assertThat(DailyPause.isValidZone("Mars/Base")).isFalse();
  }
}
