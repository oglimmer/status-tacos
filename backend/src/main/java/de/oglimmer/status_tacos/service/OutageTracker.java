/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the checks of one monitor, in time order, into outages. An outage starts at the first DOWN
 * check and ends at the next UP check.
 *
 * <p>The roll-up job uses it to write {@code monitor_outage}. The stats read path uses it for the
 * newest checks that are not rolled up yet. So both give the same result.
 */
public final class OutageTracker {

  /**
   * @param id database id, null if the outage is not stored yet
   * @param end null while the outage is open
   */
  public record Outage(Long id, LocalDateTime start, LocalDateTime end) {

    public boolean isOpen() {
      return end == null;
    }
  }

  private final List<Outage> closed = new ArrayList<>();
  private Outage open;

  /**
   * @param open the open outage before the first check, or null
   */
  public OutageTracker(Outage open) {
    this.open = open;
  }

  public void accept(LocalDateTime checkedAt, boolean up) {
    if (!up && open == null) {
      open = new Outage(null, checkedAt, null);
    } else if (up && open != null) {
      closed.add(new Outage(open.id(), open.start(), checkedAt));
      open = null;
    }
  }

  /**
   * The outages this tracker changed or created, in time order: all outages closed by the checks,
   * then the open outage, if there is one.
   */
  public List<Outage> result() {
    List<Outage> result = new ArrayList<>(closed);
    if (open != null) {
      result.add(open);
    }
    return result;
  }
}
