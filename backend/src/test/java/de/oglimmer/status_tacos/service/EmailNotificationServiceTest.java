/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EmailNotificationServiceTest {

  private static final LocalDateTime CHECKED = LocalDateTime.of(2026, 11, 3, 10, 5, 0);
  private static final LocalDateTime SINCE = CHECKED.minusMinutes(5);

  private final EmailNotificationService service =
      new EmailNotificationService("https://status.example.com/");

  private static TeamsAlert alert(TeamsAlert.Kind kind, String name, String reason) {
    return new TeamsAlert(
        kind,
        name,
        "https://shop.example.com",
        "Acme",
        503,
        reason,
        120,
        CHECKED,
        SINCE,
        30,
        null,
        null);
  }

  @Test
  void subjectNamesTheStateAndTheService() {
    assertThat(EmailNotificationService.subject(alert(TeamsAlert.Kind.DOWN, "Shop", "x")))
        .isEqualTo("Service is DOWN: Shop");
    assertThat(EmailNotificationService.subject(alert(TeamsAlert.Kind.UP, "Shop", null)))
        .isEqualTo("Service is UP: Shop");
  }

  @Test
  void plainTextHasAllFactsAndOnlyAscii() {
    String text = service.plainText(alert(TeamsAlert.Kind.DOWN, "Café Shop", "HTTP 503 — down"));

    assertThat(text)
        .contains("DOWN: Cafe Shop")
        .contains("Reason:")
        .contains("HTTP 503 - down")
        .contains("Status code:")
        .contains("503")
        .contains("Tenant:")
        .contains("Acme")
        .contains("Open Status Tacos: https://status.example.com/");
    assertThat(text.chars().allMatch(c -> c < 128)).isTrue();
  }

  @Test
  void htmlEscapesValuesAndHasTheButtons() {
    String html = service.html(alert(TeamsAlert.Kind.DOWN, "<b>Shop</b>", "x"));

    assertThat(html)
        .contains("&lt;b&gt;Shop&lt;/b&gt;")
        .doesNotContain("<b>Shop</b>")
        .contains("href=\"https://shop.example.com\"")
        .contains("href=\"https://status.example.com/\"");
  }
}
