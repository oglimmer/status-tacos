/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;

/**
 * One iOS notification. The app reads monitorId and kind to open the monitor when the user taps the
 * notification.
 *
 * @param collapseId a newer notification with the same id replaces the older one on the device
 * @param timeSensitive shown at once, also in a Focus mode (DOWN alerts)
 */
public record PushMessage(
    String title,
    String body,
    Integer monitorId,
    Integer tenantId,
    String kind,
    String collapseId,
    boolean timeSensitive) {

  /** The notification of a DOWN, UP or TEST alert. */
  public static PushMessage of(TeamsAlert alert, Integer monitorId, Integer tenantId) {
    String tenant = alert.tenantName() != null ? " · " + alert.tenantName() : "";
    return switch (alert.kind()) {
      case DOWN ->
          new PushMessage(
              "Down: " + alert.monitorName(),
              reasonText(alert) + tenant,
              monitorId,
              tenantId,
              "down",
              "monitor-" + monitorId,
              true);
      case UP ->
          new PushMessage(
              "Up again: " + alert.monitorName(),
              downtimeText(alert) + tenant,
              monitorId,
              tenantId,
              "up",
              "monitor-" + monitorId,
              false);
      case TEST ->
          new PushMessage(
              "Test notification",
              "Push alerts work" + tenant + ". " + alert.scope() + ".",
              null,
              tenantId,
              "test",
              null,
              false);
    };
  }

  private static String reasonText(TeamsAlert alert) {
    if (alert.statusCode() != null && alert.statusCode() > 0) {
      return "HTTP " + alert.statusCode();
    }
    if (alert.reason() != null && !alert.reason().isBlank()) {
      return alert.reason().length() <= 120 ? alert.reason() : alert.reason().substring(0, 120);
    }
    return "The check failed";
  }

  private static String downtimeText(TeamsAlert alert) {
    if (alert.downSince() == null || alert.checkedAt() == null) {
      return "The check works again";
    }
    return "Was down for " + formatDuration(Duration.between(alert.downSince(), alert.checkedAt()));
  }

  /** "45s", "5m", "2h 5m", "3d 4h". */
  static String formatDuration(Duration duration) {
    long seconds = Math.max(0, duration.getSeconds());
    if (seconds < 60) {
      return seconds + "s";
    }
    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + "m";
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return minutes % 60 == 0 ? hours + "h" : hours + "h " + (minutes % 60) + "m";
    }
    long days = hours / 24;
    return hours % 24 == 0 ? days + "d" : days + "d " + (hours % 24) + "h";
  }

  /** The APNs payload: the "aps" dictionary plus the custom keys of the app. */
  public String toJson(ObjectMapper mapper) {
    ObjectNode root = mapper.createObjectNode();
    ObjectNode aps = root.putObject("aps");
    ObjectNode alert = aps.putObject("alert");
    alert.put("title", title);
    alert.put("body", body);
    aps.put("sound", "default");
    aps.put("interruption-level", timeSensitive ? "time-sensitive" : "active");
    if (monitorId != null) {
      // Groups the notifications of one monitor on the device.
      aps.put("thread-id", "monitor-" + monitorId);
      root.put("monitorId", monitorId);
    }
    if (tenantId != null) {
      root.put("tenantId", tenantId);
    }
    root.put("kind", kind);
    try {
      return mapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
