/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.oglimmer.status_tacos.service.MonitorExecutionService;
import de.oglimmer.status_tacos.service.SchedulerHeartbeat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
class SchedulerHealthIndicatorTest {

  private static final long CHECK_INTERVAL_MS = 15_000;
  private static final Duration INITIAL_DELAY = Duration.ofSeconds(5);
  private static final Duration TWO_INTERVALS = Duration.ofSeconds(30);
  private static final Instant START = Instant.parse("2025-06-01T12:00:00Z");

  @Mock private MonitorExecutionService monitorExecutionService;

  private final MutableClock clock = new MutableClock(START);
  private SchedulerHeartbeat heartbeat;
  private SchedulerHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    heartbeat = new SchedulerHeartbeat(clock);
    indicator =
        new SchedulerHealthIndicator(
            monitorExecutionService, heartbeat, clock, true, CHECK_INTERVAL_MS);
    lenient().when(monitorExecutionService.getActiveMonitorCount()).thenReturn(3L);
  }

  @Test
  void upAfterStartUntilTheFirstRunIsDue() {
    clock.advance(INITIAL_DELAY.plus(TWO_INTERVALS));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("activeMonitors", 3L)
        .doesNotContainKey("lastFiredAt")
        .doesNotContainKey("lastRunFinishedAt");
  }

  @Test
  void downWhenTheJobNeverFired() {
    clock.advance(INITIAL_DELAY.plus(TWO_INTERVALS).plusSeconds(1));

    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void upWhileTheJobFires_alsoOnAReplicaThatRunsNoChecks() {
    clock.advance(Duration.ofMinutes(10));
    heartbeat.checksFired();
    clock.advance(TWO_INTERVALS);

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("lastFiredAt", START.plus(Duration.ofMinutes(10)))
        .doesNotContainKey("lastRunFinishedAt");
  }

  @Test
  void downWhenTheJobStoppedFiring() {
    heartbeat.checksFired();
    clock.advance(TWO_INTERVALS.plusSeconds(1));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("status").toString()).contains("did not fire for 31s");
  }

  @Test
  void showsTheLastRunOfThisReplica() {
    heartbeat.checksFired();
    clock.advance(Duration.ofSeconds(4));
    heartbeat.checksFinished(Duration.ofMillis(3_900));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("lastRunFinishedAt", START.plusSeconds(4))
        .containsEntry("lastRunMillis", 3_900L);
  }

  @Test
  void upWhenSchedulingIsDisabledOnThisInstance() {
    SchedulerHealthIndicator disabled =
        new SchedulerHealthIndicator(
            monitorExecutionService, heartbeat, clock, false, CHECK_INTERVAL_MS);
    clock.advance(Duration.ofHours(1));

    Health health = disabled.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("status", "Scheduling is disabled on this instance");
  }

  @Test
  void downWhenTheMonitorCountFails() {
    when(monitorExecutionService.getActiveMonitorCount()).thenThrow(new RuntimeException("no db"));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("error", "no db");
  }

  /** A clock the test can move forward. Always UTC. */
  static class MutableClock extends Clock {
    private volatile Instant instant;

    MutableClock(Instant start) {
      this.instant = start;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
