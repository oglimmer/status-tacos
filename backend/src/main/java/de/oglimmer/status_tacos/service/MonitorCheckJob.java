/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;

/**
 * One run of all monitor checks. One replica runs it at a time: the lock in the shedlock table
 * keeps the others out, and lockAtLeastFor keeps a second replica out of the same interval.
 *
 * <p>Its own bean, not a method of ScheduledMonitorService: the lock works through the Spring
 * proxy, and the scheduled method must do its part on every replica before the lock is taken.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitorCheckJob {

  private final MonitorExecutionService monitorExecutionService;
  private final SchedulerHeartbeat heartbeat;

  @SchedulerLock(name = "monitor-checks", lockAtLeastFor = "PT10S", lockAtMostFor = "PT2M")
  public void run() {
    log.debug("Starting scheduled monitor checks");
    try {
      long startTime = System.nanoTime();
      monitorExecutionService.executeAllActiveMonitors();
      Duration took = Duration.ofNanos(System.nanoTime() - startTime);
      heartbeat.checksFinished(took);
      log.debug("Completed scheduled monitor checks in {}ms", took.toMillis());
    } catch (Exception e) {
      log.error("Error during scheduled monitor checks: {}", e.getMessage(), e);
    }
  }
}
