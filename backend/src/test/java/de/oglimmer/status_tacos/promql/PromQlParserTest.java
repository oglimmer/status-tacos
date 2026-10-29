/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import de.oglimmer.status_tacos.promql.Expr.Aggregation;
import de.oglimmer.status_tacos.promql.Expr.Binary;
import de.oglimmer.status_tacos.promql.Expr.Call;
import de.oglimmer.status_tacos.promql.Expr.MatrixSelector;
import de.oglimmer.status_tacos.promql.Expr.NumberLiteral;
import de.oglimmer.status_tacos.promql.Expr.VectorSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PromQlParserTest {

  @Test
  void selectorWithMatchers() {
    VectorSelector selector =
        (VectorSelector)
            PromQlParser.parse("probe_success{monitor=~\"shop.*\", tenant!='Dev', job=\"x\",}");

    assertThat(selector.matchers())
        .extracting(LabelMatcher::name, LabelMatcher::type, LabelMatcher::value)
        .containsExactly(
            tuple("__name__", LabelMatcher.Type.EQ, "probe_success"),
            tuple("monitor", LabelMatcher.Type.RE, "shop.*"),
            tuple("tenant", LabelMatcher.Type.NEQ, "Dev"),
            tuple("job", LabelMatcher.Type.EQ, "x"));
  }

  @Test
  void selectorWithoutMetricName() {
    VectorSelector selector =
        (VectorSelector) PromQlParser.parse("{__name__=~\"probe_.*\", monitor=\"Shop\"}");

    assertThat(selector.matchers()).hasSize(2);
  }

  @Test
  void regexMatchesTheFullValue() {
    LabelMatcher matcher = LabelMatcher.of("monitor", LabelMatcher.Type.RE, "shop|blog");

    assertThat(matcher.matches("shop")).isTrue();
    assertThat(matcher.matches("shopping")).isFalse();
  }

  @Test
  void functionWithRange() {
    Call call = (Call) PromQlParser.parse("avg_over_time(probe_success[1h30m])");

    assertThat(call.function()).isEqualTo("avg_over_time");
    MatrixSelector range = (MatrixSelector) call.args().getFirst();
    assertThat(range.rangeMillis()).isEqualTo(5_400_000L);
  }

  @Test
  void aggregationWithByBeforeOrAfter() {
    Aggregation before = (Aggregation) PromQlParser.parse("avg by (tenant) (probe_success)");
    Aggregation after = (Aggregation) PromQlParser.parse("avg(probe_success) without (instance)");

    assertThat(before.grouping()).containsExactly("tenant");
    assertThat(before.without()).isFalse();
    assertThat(after.grouping()).containsExactly("instance");
    assertThat(after.without()).isTrue();
  }

  @Test
  void precedence() {
    Binary sum = (Binary) PromQlParser.parse("1 + 2 * 3");
    assertThat(sum.op()).isEqualTo("+");
    assertThat(((Binary) sum.rhs()).op()).isEqualTo("*");

    Binary comparison = (Binary) PromQlParser.parse("probe_success * 100 < bool 50");
    assertThat(comparison.op()).isEqualTo("<");
    assertThat(comparison.returnBool()).isTrue();

    assertThat(PromQlParser.parse("-2")).isEqualTo(new NumberLiteral(-2));
    assertThat(PromQlParser.parse("1e3")).isEqualTo(new NumberLiteral(1000));
  }

  @Test
  void durations() {
    assertThat(PromQlParser.parseDurationMillis("15s")).isEqualTo(15_000);
    assertThat(PromQlParser.parseDurationMillis("1m30s")).isEqualTo(90_000);
    assertThat(PromQlParser.parseDurationMillis("250ms")).isEqualTo(250);
    assertThat(PromQlParser.parseDurationMillis("60")).isEqualTo(60_000);
    assertThat(PromQlParser.parseDurationMillis("1.5")).isEqualTo(1_500);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "{monitor=~\".*\"}",
        "probe_success offset 5m",
        "probe_success[5m:1m]",
        "probe_success{monitor=\"x\"",
        "probe_success{monitor=~\"(\"}",
        "probe_success @ 123",
        "\"text\"",
        "sum(probe_success",
        "(1 + 2",
        "rate(probe_success)[5m]"
      })
  void invalidQueries(String query) {
    assertThatThrownBy(() -> PromQlParser.parse(query)).isInstanceOf(PromQlException.class);
  }

  @Test
  void invalidDuration() {
    assertThatThrownBy(() -> PromQlParser.parseDurationMillis("5x"))
        .isInstanceOf(PromQlException.class);
  }
}
