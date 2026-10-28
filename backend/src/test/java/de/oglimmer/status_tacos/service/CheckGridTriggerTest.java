/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.SimpleTriggerContext;

class CheckGridTriggerTest {

  private final CheckGridTrigger trigger = new CheckGridTrigger(15_000, 5_000);

  @Test
  void firstRunIsTheFirstGridMomentAfterTheInitialDelay() {
    assertThat(next(null, 101_000)).isEqualTo(Instant.ofEpochMilli(120_000));
    assertThat(next(null, 109_000)).isEqualTo(Instant.ofEpochMilli(120_000));
    assertThat(next(null, 116_000)).isEqualTo(Instant.ofEpochMilli(135_000));
  }

  @Test
  void nextRunIsTheNextGridMoment() {
    assertThat(next(120_000L, 121_500)).isEqualTo(Instant.ofEpochMilli(135_000));
  }

  @Test
  void runThatTakesTooLongSkipsAGridMoment() {
    assertThat(next(120_000L, 136_000)).isEqualTo(Instant.ofEpochMilli(150_000));
  }

  @Test
  void clockThatGoesBackDoesNotFireTwice() {
    // The run started at 120 s, then the clock went back to 119.8 s
    assertThat(next(120_000L, 119_800)).isEqualTo(Instant.ofEpochMilli(135_000));
  }

  private Instant next(Long lastScheduledMs, long nowMs) {
    Clock clock = Clock.fixed(Instant.ofEpochMilli(nowMs), ZoneOffset.UTC);
    SimpleTriggerContext context = new SimpleTriggerContext(clock);
    if (lastScheduledMs != null) {
      Instant last = Instant.ofEpochMilli(lastScheduledMs);
      context.update(last, last, Instant.ofEpochMilli(nowMs));
    }
    return trigger.nextExecution(context);
  }
}
