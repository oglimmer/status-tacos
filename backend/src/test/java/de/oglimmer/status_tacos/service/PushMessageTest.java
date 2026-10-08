/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PushMessageTest {

  private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 8, 10, 0);

  private static TeamsAlert alert(TeamsAlert.Kind kind, Integer status, String reason) {
    return new TeamsAlert(
        kind,
        "Shop",
        "https://shop",
        "Prod",
        status,
        reason,
        120,
        T0.plusMinutes(12),
        T0,
        60,
        "iOS push",
        "All monitors of the tenant");
  }

  @Test
  void downShowsTheStatusCode() {
    PushMessage message = PushMessage.of(alert(TeamsAlert.Kind.DOWN, 503, null), 7, 1);

    assertThat(message.title()).isEqualTo("Down: Shop");
    assertThat(message.body()).isEqualTo("HTTP 503 · Prod");
    assertThat(message.timeSensitive()).isTrue();
    assertThat(message.collapseId()).isEqualTo("monitor-7");
    assertThat(message.kind()).isEqualTo("down");
  }

  @Test
  void downWithoutStatusCodeShowsTheReason() {
    assertThat(PushMessage.of(alert(TeamsAlert.Kind.DOWN, 0, "Connection refused"), 7, 1).body())
        .isEqualTo("Connection refused · Prod");
    assertThat(PushMessage.of(alert(TeamsAlert.Kind.DOWN, null, null), 7, 1).body())
        .isEqualTo("The check failed · Prod");
    assertThat(PushMessage.of(alert(TeamsAlert.Kind.DOWN, null, "x".repeat(300)), 7, 1).body())
        .hasSize(120 + " · Prod".length());
  }

  @Test
  void upShowsHowLongTheMonitorWasDown() {
    PushMessage message = PushMessage.of(alert(TeamsAlert.Kind.UP, 200, null), 7, 1);

    assertThat(message.title()).isEqualTo("Up again: Shop");
    assertThat(message.body()).isEqualTo("Was down for 12m · Prod");
    assertThat(message.timeSensitive()).isFalse();
    // Replaces the DOWN notification of the same monitor.
    assertThat(message.collapseId()).isEqualTo("monitor-7");
  }

  @Test
  void testHasNoMonitorToOpen() throws Exception {
    PushMessage message = PushMessage.of(alert(TeamsAlert.Kind.TEST, null, null), null, 1);

    assertThat(message.title()).isEqualTo("Test notification");
    assertThat(message.body()).isEqualTo("Push alerts work · Prod. All monitors of the tenant.");
    JsonNode json = new ObjectMapper().readTree(message.toJson(new ObjectMapper()));
    assertThat(json.has("monitorId")).isFalse();
    assertThat(json.at("/aps/thread-id").isMissingNode()).isTrue();
    assertThat(json.get("tenantId").asInt()).isEqualTo(1);
  }

  @Test
  void durations() {
    assertThat(PushMessage.formatDuration(Duration.ofSeconds(45))).isEqualTo("45s");
    assertThat(PushMessage.formatDuration(Duration.ofMinutes(5))).isEqualTo("5m");
    assertThat(PushMessage.formatDuration(Duration.ofMinutes(125))).isEqualTo("2h 5m");
    assertThat(PushMessage.formatDuration(Duration.ofHours(2))).isEqualTo("2h");
    assertThat(PushMessage.formatDuration(Duration.ofHours(76))).isEqualTo("3d 4h");
    assertThat(PushMessage.formatDuration(Duration.ofSeconds(-5))).isEqualTo("0s");
  }
}
