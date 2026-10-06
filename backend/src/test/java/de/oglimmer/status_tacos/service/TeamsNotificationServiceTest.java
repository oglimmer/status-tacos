/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class TeamsNotificationServiceTest {

  private static final String WORKFLOW_PATH =
      "/workflows/abc/triggers/manual/paths/invoke"
          + "?api-version=2016-06-01&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=s1g%2Bn%3D";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockWebServer mockWebServer;
  private TeamsNotificationService service;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    service =
        new TeamsNotificationService(
            RestClient.builder(), objectMapper, "https://tacos.example.com/");
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void sendKeepsPreEncodedSignatureAndPostsAdaptiveCardEnvelope() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(202));

    service.send(workflowUrl(), downAlert());

    RecordedRequest request = mockWebServer.takeRequest();
    assertEquals("POST", request.getMethod());
    // A re-encoded '%' (%25) breaks the Power Automate signature
    assertEquals(WORKFLOW_PATH, request.getPath());
    assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
    // Fixed length, not chunked: webhook endpoints do not always accept chunked bodies
    assertNull(request.getHeader("Transfer-Encoding"));
    assertEquals(request.getBodySize(), Long.parseLong(request.getHeader("Content-Length")));

    JsonNode payload = objectMapper.readTree(request.getBody().readUtf8());
    assertEquals("message", payload.get("type").asText());
    assertEquals(
        "🔴 DOWN: Shop API",
        payload.at("/attachments/0/content/body/0/text").asText(),
        "emoji must arrive as UTF-8");
    assertEquals(2, payload.size(), "extra top-level keys can make the flow reject the request");
    JsonNode attachment = payload.get("attachments").get(0);
    assertEquals("application/vnd.microsoft.card.adaptive", attachment.get("contentType").asText());
    assertTrue(attachment.has("contentUrl"));
    assertTrue(attachment.get("contentUrl").isNull());
    JsonNode card = attachment.get("content");
    assertEquals("AdaptiveCard", card.get("type").asText());
    assertEquals("Full", card.get("msteams").get("width").asText());
  }

  @Test
  void sendThrowsWhenTeamsRejectsTheCard() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody("bad schema"));

    assertThrows(RestClientException.class, () -> service.send(workflowUrl(), downAlert()));
  }

  @Test
  void downCardShowsReasonOutageAndLinks() throws Exception {
    JsonNode card = card(downAlert());

    JsonNode headline = card.get("body").get(0);
    assertEquals("🔴 DOWN: Shop API", headline.get("text").asText());
    assertEquals("Attention", headline.get("color").asText());
    assertTrue(headline.get("wrap").asBoolean());
    assertEquals(
        "The health check failed: HTTP 503 response", card.get("body").get(1).get("text").asText());

    JsonNode facts = card.get("body").get(2).get("facts");
    assertEquals("Shop API", fact(facts, "Monitor"));
    assertEquals("https://shop.example.com/health", fact(facts, "URL"));
    assertEquals("Acme", fact(facts, "Tenant"));
    assertEquals("HTTP 503 response", fact(facts, "Reason"));
    assertEquals("503", fact(facts, "Status code"));
    assertEquals("812 ms", fact(facts, "Response time"));
    assertTrue(fact(facts, "Down since").endsWith("(1 min 30 s)"));
    assertEquals("30 s", fact(facts, "Alert threshold"));
    assertNotNull(fact(facts, "Checked at"));

    JsonNode actions = card.get("actions");
    assertEquals("https://shop.example.com/health", actions.get(0).get("url").asText());
    assertEquals("https://tacos.example.com/", actions.get(1).get("url").asText());
  }

  @Test
  void upCardShowsDowntime() throws Exception {
    LocalDateTime downSince = LocalDateTime.of(2026, 10, 6, 10, 0, 0);
    LocalDateTime upAt = downSince.plusHours(1).plusMinutes(5);
    TeamsAlert alert =
        new TeamsAlert(
            TeamsAlert.Kind.UP,
            "Shop API",
            "https://shop.example.com/health",
            "Acme",
            200,
            null,
            95,
            upAt,
            downSince,
            30,
            null,
            null);

    JsonNode card = card(alert);

    assertEquals("✅ UP again: Shop API", card.get("body").get(0).get("text").asText());
    assertEquals("Good", card.get("body").get(0).get("color").asText());
    assertEquals(
        "The health check passes again after 1 h 5 min of downtime.",
        card.get("body").get(1).get("text").asText());
    JsonNode facts = card.get("body").get(2).get("facts");
    assertEquals("1 h 5 min", fact(facts, "Downtime"));
    assertNull(fact(facts, "Reason"));
    assertNull(fact(facts, "Alert threshold"));
  }

  @Test
  void testCardShowsContactAndScope() throws Exception {
    TeamsAlert alert =
        new TeamsAlert(
            TeamsAlert.Kind.TEST,
            null,
            null,
            "Acme",
            null,
            null,
            null,
            LocalDateTime.now(),
            null,
            null,
            "Ops channel",
            "2 selected: Billing, Shop API");

    JsonNode card = card(alert);

    assertEquals("🧪 Test notification: Ops channel", card.get("body").get(0).get("text").asText());
    JsonNode facts = card.get("body").get(2).get("facts");
    assertEquals("Ops channel", fact(facts, "Alert contact"));
    assertEquals("2 selected: Billing, Shop API", fact(facts, "Sends alerts for"));
    // No monitored URL on a test card, only the link to Status Tacos
    assertEquals(1, card.get("actions").size());
  }

  @Test
  void formatDuration() {
    LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    assertEquals("0 s", TeamsNotificationService.formatDuration(start, start));
    assertEquals("45 s", TeamsNotificationService.formatDuration(start, start.plusSeconds(45)));
    assertEquals("2 min", TeamsNotificationService.formatDuration(start, start.plusMinutes(2)));
    assertEquals(
        "3 h 7 min",
        TeamsNotificationService.formatDuration(start, start.plusHours(3).plusMinutes(7)));
    assertEquals(
        "2 d 1 h", TeamsNotificationService.formatDuration(start, start.plusDays(2).plusHours(1)));
  }

  private String workflowUrl() {
    return mockWebServer.url("/").toString().replaceAll("/$", "") + WORKFLOW_PATH;
  }

  private TeamsAlert downAlert() {
    LocalDateTime checkedAt = LocalDateTime.of(2026, 10, 6, 10, 1, 30);
    return new TeamsAlert(
        TeamsAlert.Kind.DOWN,
        "Shop API",
        "https://shop.example.com/health",
        "Acme",
        503,
        "HTTP 503 response",
        812,
        checkedAt,
        checkedAt.minusSeconds(90),
        30,
        null,
        null);
  }

  private JsonNode card(TeamsAlert alert) {
    JsonNode payload = objectMapper.valueToTree(service.buildPayload(alert));
    return payload.get("attachments").get(0).get("content");
  }

  private String fact(JsonNode facts, String title) {
    for (JsonNode fact : facts) {
      if (title.equals(fact.get("title").asText())) {
        return fact.get("value").asText();
      }
    }
    return null;
  }
}
