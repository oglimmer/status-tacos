/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalDouble;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PrometheusParserTest {

  private static OptionalDouble sum(String text, String key) {
    Pattern pattern = Pattern.compile(key);
    return PrometheusParser.sumMatching(text, series -> pattern.matcher(series).find());
  }

  @Test
  void sumsAllMatchingSamples() {
    String text =
        """
        # HELP http_requests_total Requests
        # TYPE http_requests_total counter
        http_requests_total{code="200"} 10
        http_requests_total{code="500"} 2
        other_metric 100
        """;

    assertThat(sum(text, "^http_requests_total")).hasValue(12.0);
  }

  @Test
  void keyCanMatchLabels() {
    String text = "up{job=\"api\"} 1\nup{job=\"db\"} 0\n";

    assertThat(sum(text, "job=\"db\"")).hasValue(0.0);
  }

  @Test
  void ignoresTheTimestamp() {
    assertThat(sum("queue_size 5 1700000000000\n", "queue_size")).hasValue(5.0);
  }

  @Test
  void labelValuesCanHoldSpacesBracesAndEscapedQuotes() {
    String text = "requests{path=\"/a b\",msg=\"say \\\"hi}\\\"\"} 3\n";

    assertThat(sum(text, "^requests")).hasValue(3.0);
  }

  @Test
  void lineWithoutValueIsSkipped() {
    assertThat(sum("broken_line\nqueue_size 4\n", "")).hasValue(4.0);
  }

  @Test
  void infinityIsReadAndNanIsSkipped() {
    assertThat(sum("a +Inf\n", "^a")).hasValue(Double.POSITIVE_INFINITY);
    assertThat(sum("a NaN\na 2\n", "^a")).hasValue(2.0);
  }

  @Test
  void noMatchingSampleGivesEmpty() {
    assertThat(sum("other 1\n", "^queue")).isEmpty();
  }

  @Test
  void handlesWindowsLineEnds() {
    assertThat(sum("a 1\r\nb 2\r\n", "^[ab]")).hasValue(3.0);
  }
}
