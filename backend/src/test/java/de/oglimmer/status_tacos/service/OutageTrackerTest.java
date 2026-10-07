/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.oglimmer.status_tacos.service.OutageTracker.Outage;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OutageTrackerTest {

  private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 10, 0);

  private static LocalDateTime t(int seconds) {
    return T0.plusSeconds(seconds);
  }

  @Test
  void onlyUpChecks_noOutage() {
    OutageTracker tracker = new OutageTracker(null);
    tracker.accept(t(0), true);
    tracker.accept(t(15), true);

    assertThat(tracker.result()).isEmpty();
  }

  @Test
  void downThenUp_closedOutage() {
    OutageTracker tracker = new OutageTracker(null);
    tracker.accept(t(0), true);
    tracker.accept(t(15), false);
    tracker.accept(t(30), false);
    tracker.accept(t(45), true);

    assertThat(tracker.result()).containsExactly(new Outage(null, t(15), t(45)));
  }

  @Test
  void downAtEnd_openOutage() {
    OutageTracker tracker = new OutageTracker(null);
    tracker.accept(t(0), false);

    assertThat(tracker.result()).containsExactly(new Outage(null, t(0), null));
  }

  @Test
  void existingOpenOutage_closedWithItsId() {
    OutageTracker tracker = new OutageTracker(new Outage(7L, t(-100), null));
    tracker.accept(t(0), false);
    tracker.accept(t(15), true);

    assertThat(tracker.result()).containsExactly(new Outage(7L, t(-100), t(15)));
  }

  @Test
  void existingOpenOutage_staysOpen() {
    Outage open = new Outage(7L, t(-100), null);
    OutageTracker tracker = new OutageTracker(open);
    tracker.accept(t(0), false);

    assertThat(tracker.result()).containsExactly(open);
  }

  @Test
  void alternatingChecks_manyOutagesInOrder() {
    OutageTracker tracker = new OutageTracker(new Outage(7L, t(-100), null));
    tracker.accept(t(0), true);
    tracker.accept(t(15), false);
    tracker.accept(t(30), true);
    tracker.accept(t(45), false);

    assertThat(tracker.result())
        .containsExactly(
            new Outage(7L, t(-100), t(0)),
            new Outage(null, t(15), t(30)),
            new Outage(null, t(45), null));
  }
}
