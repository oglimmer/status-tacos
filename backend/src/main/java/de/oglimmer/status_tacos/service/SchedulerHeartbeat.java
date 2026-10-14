/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * Remembers when the check job last fired on this instance and when this instance last ran all
 * checks.
 *
 * <p>The job fires on every replica, but only the replica that holds the lock runs the checks. So
 * the fire time is the sign of life of this instance's scheduler, and the last run is known only on
 * the replica that ran it.
 */
@Component
public class SchedulerHeartbeat {

  /** One finished run of all checks. */
  public record Run(Instant finishedAt, Duration took) {}

  private final Clock clock;

  /** When this instance started. Before the first run it counts as the last sign of life. */
  @Getter private final Instant startedAt;

  private final AtomicReference<Instant> lastFiredAt = new AtomicReference<>();
  private final AtomicReference<Run> lastRun = new AtomicReference<>();

  public SchedulerHeartbeat(Clock clock) {
    this.clock = clock;
    this.startedAt = clock.instant();
  }

  /** The check job fired on this instance, with or without the lock. */
  public void checksFired() {
    lastFiredAt.set(clock.instant());
  }

  /** This instance ran all checks. */
  public void checksFinished(Duration took) {
    lastRun.set(new Run(clock.instant(), took));
  }

  /**
   * @return when the check job last fired on this instance, or null if it never did
   */
  public Instant getLastFiredAt() {
    return lastFiredAt.get();
  }

  /**
   * @return the last run of this instance, or null if it ran none
   */
  public Run getLastRun() {
    return lastRun.get();
  }
}
