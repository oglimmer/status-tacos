/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Sends alerts to a Microsoft Teams channel or chat through a Teams "Workflows" (Power Automate)
 * webhook. The legacy Office 365 connector format ({@code {"text": ...}}) is no longer accepted, so
 * the payload is an Adaptive Card wrapped in a message envelope.
 */
@Slf4j
@Service
public class TeamsNotificationService {

  /** Always with the zone: the containers run in UTC, a reader's local clock does not. */
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz", Locale.ROOT);

  /** Keeps a long error text from pushing the facts out of the visible card. */
  private static final int MAX_FACT_LENGTH = 1000;

  private final RestClient restClient;

  private final ObjectMapper objectMapper;

  private final String appUrl;

  public TeamsNotificationService(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${monitor.app-url:}") String appUrl) {
    // The alert is sent while the check transaction is open, so a hanging webhook must not
    // block the monitor scheduler.
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(10));
    this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    this.objectMapper = objectMapper;
    this.appUrl = appUrl == null ? "" : appUrl.replaceAll("/+$", "");
  }

  /**
   * Posts the card to the workflow URL.
   *
   * @throws org.springframework.web.client.RestClientException when Teams does not accept it
   */
  public void send(String workflowUrl, TeamsAlert alert) {
    // Power Automate answers 202 Accepted, usually without a body: don't try to read one
    ResponseEntity<Void> response =
        restClient
            .post()
            // URI.create keeps the pre-encoded query (sp=%2Ftriggers...) intact;
            // passing the String would let RestClient re-encode '%' to '%25' and break the
            // signature.
            .uri(URI.create(workflowUrl))
            .contentType(MediaType.APPLICATION_JSON)
            // Pre-serialized UTF-8 bytes are sent with a Content-Length. A Map body would be
            // streamed with chunked transfer encoding, which webhook endpoints do not always
            // accept.
            .body(toJson(alert))
            .retrieve()
            .toBodilessEntity();
    log.debug(
        "Teams workflow answered {} for {} alert of monitor '{}'",
        response.getStatusCode(),
        alert.kind(),
        alert.monitorName());
  }

  private byte[] toJson(TeamsAlert alert) {
    try {
      return objectMapper.writeValueAsBytes(buildPayload(alert));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize the Teams card", e);
    }
  }

  /**
   * Builds the Power Automate / Teams workflow payload: a coloured headline, one sentence what
   * happened, a fact list with everything needed to act on the alert, and buttons to open the
   * monitored URL and Status Tacos. Every text block wraps, otherwise Teams cuts the message off
   * after the first line.
   */
  Map<String, Object> buildPayload(TeamsAlert alert) {
    List<Map<String, Object>> body = new ArrayList<>();
    body.add(
        Map.of(
            "type",
            "TextBlock",
            "text",
            headline(alert),
            "weight",
            "Bolder",
            "size",
            "Medium",
            "color",
            color(alert.kind()),
            "wrap",
            true));
    body.add(Map.of("type", "TextBlock", "text", summary(alert), "wrap", true, "spacing", "Small"));
    body.add(
        Map.of(
            "type", "FactSet",
            "facts", facts(alert),
            "spacing", "Medium"));

    Map<String, Object> card = new LinkedHashMap<>();
    card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
    card.put("type", "AdaptiveCard");
    card.put("version", "1.2");
    card.put("body", body);
    List<Map<String, Object>> actions = actions(alert);
    if (!actions.isEmpty()) {
      card.put("actions", actions);
    }
    // Teams renders a narrow card by default, which wraps the facts into many short lines.
    card.put("msteams", Map.of("width", "Full"));

    // Use a null-permitting map (Map.of rejects nulls) so "contentUrl": null is serialized,
    // matching the sample the Power Automate HTTP trigger schema is generated from.
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
    attachment.put("contentUrl", null);
    attachment.put("content", card);
    // Keep the envelope exactly as the Power Automate HTTP trigger schema expects it:
    // extra top-level keys can make the flow reject the request.
    return Map.of("type", "message", "attachments", List.of(attachment));
  }

  private String headline(TeamsAlert alert) {
    return switch (alert.kind()) {
      case DOWN -> "🔴 DOWN: " + alert.monitorName();
      case UP -> "✅ UP again: " + alert.monitorName();
      case TEST -> "🧪 Test notification: " + alert.contactName();
    };
  }

  private String color(TeamsAlert.Kind kind) {
    return switch (kind) {
      case DOWN -> "Attention";
      case UP -> "Good";
      case TEST -> "Accent";
    };
  }

  private String summary(TeamsAlert alert) {
    return switch (alert.kind()) {
      case DOWN ->
          alert.reason() == null || alert.reason().isBlank()
              ? "The health check failed."
              : "The health check failed: " + shorten(alert.reason());
      case UP ->
          alert.downSince() == null
              ? "The health check passes again."
              : "The health check passes again after "
                  + formatDuration(alert.downSince(), alert.checkedAt())
                  + " of downtime.";
      case TEST ->
          "This is a test from Status Tacos. When you can read this card, Teams alerts for this"
              + " contact work.";
    };
  }

  private List<Map<String, String>> facts(TeamsAlert alert) {
    List<Map<String, String>> facts = new ArrayList<>();
    if (alert.kind() == TeamsAlert.Kind.TEST) {
      facts.add(fact("Alert contact", alert.contactName()));
      facts.add(fact("Tenant", alert.tenantName()));
      facts.add(fact("Sends alerts for", alert.scope()));
      facts.add(fact("Sent at", format(alert.checkedAt())));
      return facts;
    }

    facts.add(fact("Monitor", alert.monitorName()));
    facts.add(fact("URL", alert.monitorUrl()));
    facts.add(fact("Tenant", alert.tenantName()));
    if (alert.kind() == TeamsAlert.Kind.DOWN && alert.reason() != null) {
      facts.add(fact("Reason", shorten(alert.reason())));
    }
    if (alert.statusCode() != null && alert.statusCode() > 0) {
      facts.add(fact("Status code", String.valueOf(alert.statusCode())));
    }
    if (alert.responseTimeMs() != null) {
      facts.add(fact("Response time", alert.responseTimeMs() + " ms"));
    }
    if (alert.downSince() != null) {
      String since = format(alert.downSince());
      facts.add(
          fact(
              "Down since",
              alert.kind() == TeamsAlert.Kind.DOWN
                  ? since + " (" + formatDuration(alert.downSince(), alert.checkedAt()) + ")"
                  : since));
    }
    if (alert.kind() == TeamsAlert.Kind.UP) {
      facts.add(fact("Up since", format(alert.checkedAt())));
      if (alert.downSince() != null) {
        facts.add(fact("Downtime", formatDuration(alert.downSince(), alert.checkedAt())));
      }
    } else {
      if (alert.alertingThresholdSeconds() != null) {
        facts.add(fact("Alert threshold", alert.alertingThresholdSeconds() + " s"));
      }
      facts.add(fact("Checked at", format(alert.checkedAt())));
    }
    return facts;
  }

  private List<Map<String, Object>> actions(TeamsAlert alert) {
    List<Map<String, Object>> actions = new ArrayList<>();
    if (alert.kind() != TeamsAlert.Kind.TEST && isHttpUrl(alert.monitorUrl())) {
      actions.add(openUrl("Open monitored URL", alert.monitorUrl()));
    }
    if (isHttpUrl(appUrl)) {
      actions.add(openUrl("Open Status Tacos", appUrl + "/"));
    }
    return actions;
  }

  private Map<String, Object> openUrl(String title, String url) {
    return Map.of("type", "Action.OpenUrl", "title", title, "url", url);
  }

  private Map<String, String> fact(String title, String value) {
    return Map.of("title", title, "value", value == null || value.isBlank() ? "-" : value);
  }

  private String format(LocalDateTime time) {
    // LocalDateTime values come from the JVM clock / database in the JVM default zone.
    return time == null ? "-" : time.atZone(ZoneId.systemDefault()).format(TIMESTAMP);
  }

  static String formatDuration(LocalDateTime from, LocalDateTime to) {
    if (from == null || to == null) {
      return "-";
    }
    long seconds = Math.max(0, Duration.between(from, to).toSeconds());
    long days = seconds / 86_400;
    long hours = (seconds % 86_400) / 3_600;
    long minutes = (seconds % 3_600) / 60;
    long rest = seconds % 60;
    StringBuilder text = new StringBuilder();
    if (days > 0) {
      text.append(days).append(" d ");
    }
    if (hours > 0) {
      text.append(hours).append(" h ");
    }
    if (minutes > 0) {
      text.append(minutes).append(" min ");
    }
    if (days == 0 && hours == 0 && (rest > 0 || minutes == 0)) {
      text.append(rest).append(" s");
    }
    return text.toString().trim();
  }

  private String shorten(String text) {
    String singleLine = text.strip();
    return singleLine.length() <= MAX_FACT_LENGTH
        ? singleLine
        : singleLine.substring(0, MAX_FACT_LENGTH) + " […]";
  }

  private boolean isHttpUrl(String url) {
    return url != null && url.matches("^https?://.+");
  }
}
