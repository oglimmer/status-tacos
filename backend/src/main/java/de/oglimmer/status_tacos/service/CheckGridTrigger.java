/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.time.Instant;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

/**
 * Fires the check job at the multiples of the check interval in wall-clock time (for 15 s: at :00,
 * :15, :30 and :45). All replicas then fire at the same moments, and a run knows its slot from the
 * clock alone (see MonitorExecutionService.slotOf). A fixed rate would start at a different moment
 * on each replica.
 *
 * <p>A run that takes too long skips the next moment, the same as with a fixed rate.
 */
public record CheckGridTrigger(long intervalMs, long initialDelayMs) implements Trigger {

  @Override
  public Instant nextExecution(TriggerContext triggerContext) {
    long now = triggerContext.getClock().millis();
    Instant last = triggerContext.lastScheduledExecution();
    // Half an interval after the last run: a clock that goes back a little never fires twice
    long earliest =
        last == null ? now + initialDelayMs : Math.max(now, last.toEpochMilli() + intervalMs / 2);
    return Instant.ofEpochMilli((Math.floorDiv(earliest, intervalMs) + 1) * intervalMs);
  }
}
