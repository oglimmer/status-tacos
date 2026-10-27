/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.oglimmer.status_tacos.dto.PublicMonitorStatusDto;
import de.oglimmer.status_tacos.dto.PublicStatusPageDto;
import de.oglimmer.status_tacos.dto.StatusType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusPageRendererTest {

  private final StatusPageRenderer renderer =
      new StatusPageRenderer(Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC));

  private static final PublicStatusPageDto PAGE =
      new PublicStatusPageDto(
          "Acme <Corp>",
          List.of(
              new PublicMonitorStatusDto("API", StatusType.up, null, new BigDecimal("99.95")),
              new PublicMonitorStatusDto(
                  "<img src=x onerror=alert(1)>",
                  StatusType.down,
                  LocalDateTime.of(2026, 10, 18, 9, 55),
                  new BigDecimal("97.10"))));

  @Test
  void defaultTemplate_showsTheData() {
    String html = renderer.render(PAGE, null, null, true).html();

    assertThat(html)
        .contains("<title>Acme &lt;Corp&gt; status</title>")
        .contains("1 system down")
        .contains("99.95%")
        .contains("Down since <time datetime=\"2026-10-18T09:55:00Z\">2026-10-18 09:55 UTC</time>")
        .contains("(2h 5m)")
        .contains("<meta http-equiv=\"refresh\" content=\"60\">")
        .contains("@layer base, tenant;");
  }

  @Test
  void data_isEscaped() {
    String html = renderer.render(PAGE, null, null, false).html();

    assertThat(html).doesNotContain("<img src=x").contains("&lt;img src=x onerror=alert(1)&gt;");
  }

  @Test
  void template_isCleaned() {
    String template =
        "<script>alert(1)</script><p onclick=\"x()\" class=\"ok\">{{tenantName}}</p>"
            + "<a href=\"javascript:alert(1)\">bad</a><a href=\"https://acme.example\">good</a>"
            + "<img src=\"data:image/png;base64,AAAA\" alt=\"logo\"><iframe src=\"https://x\"></iframe>"
            + "<style>body{}</style><form><input></form>";

    StatusPageRenderer.Rendered rendered = renderer.render(PAGE, template, null, false);

    assertThat(rendered.html())
        .doesNotContain("<script", "alert(1)</script>", "onclick", "javascript:", "<iframe")
        .doesNotContain("<form", "<input", "<style>body{}")
        .contains("<p class=\"ok\">Acme &lt;Corp&gt;</p>")
        .contains("<a href=\"https://acme.example\">good</a>")
        .contains("src=\"data:image/png;base64,AAAA\"");
    assertThat(rendered.removed())
        .contains("<script>", "onclick on <p>", "<iframe>", "<style>", "<form>", "<input>");
  }

  @Test
  void tenantCss_isInTheTenantLayer() {
    String html = renderer.render(PAGE, null, ":root { --brand: #ff6600; }", false).html();

    assertThat(html).contains("@layer tenant {\n:root { --brand: #ff6600; }\n}");
  }

  @Test
  void assetReferences_becomeUrls_andUnknownNamesAreReported() {
    Map<String, String> urls =
        Map.of("inter.woff2", "/api/v1/public/acme/status/assets/inter.woff2?v=1");
    String template = "<img src=\"asset:inter.woff2\" alt=\"logo\"><img src=\"asset:missing.png\">";
    String css = "@font-face { src: url(asset:inter.woff2) format(\"woff2\"); }";

    StatusPageRenderer.Rendered rendered = renderer.render(PAGE, template, css, urls, false);

    assertThat(rendered.html())
        .contains("<img src=\"/api/v1/public/acme/status/assets/inter.woff2?v=1\" alt=\"logo\">")
        .contains("src: url(/api/v1/public/acme/status/assets/inter.woff2?v=1) format")
        .contains("font-src 'self' https: data:");
    assertThat(rendered.unknownAssets()).containsExactly("missing.png");
    assertThat(rendered.removed()).isEmpty();
  }

  @Test
  void css_cannotCloseTheStyleElement() {
    assertThatThrownBy(() -> renderer.validate(null, "</style><script>alert(1)</script>"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("<");
  }

  @Test
  void invalidTemplate_isRejected() {
    assertThatThrownBy(() -> renderer.validate("{{#monitors}}no end", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("The template is invalid");
    assertThatThrownBy(() -> renderer.validate("{{> other}}", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tooLong_isRejected() {
    String tooLong = "a".repeat(StatusPageRenderer.MAX_CSS_LENGTH + 1);
    assertThatThrownBy(() -> renderer.validate(null, tooLong))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> renderer.validate(tooLong, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void duration() {
    assertThat(StatusPageRenderer.duration(Duration.ofMinutes(5))).isEqualTo("5m");
    assertThat(StatusPageRenderer.duration(Duration.ofMinutes(125))).isEqualTo("2h 5m");
    assertThat(StatusPageRenderer.duration(Duration.ofHours(50))).isEqualTo("2d 2h");
  }
}
