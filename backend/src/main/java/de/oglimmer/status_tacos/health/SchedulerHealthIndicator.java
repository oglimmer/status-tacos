/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.health;

import de.oglimmer.status_tacos.service.MonitorExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulerHealthIndicator implements HealthIndicator {

  private final MonitorExecutionService monitorExecutionService;

  @Override
  public Health health() {
    try {
      long activeMonitors = monitorExecutionService.getActiveMonitorCount();

      return Health.up()
          .withDetail("activeMonitors", activeMonitors)
          .withDetail("status", "Scheduler is operational")
          .build();

    } catch (Exception e) {
      log.error("Scheduler health check failed: {}", e.getMessage(), e);

      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("status", "Scheduler health check failed")
          .build();
    }
  }
}
