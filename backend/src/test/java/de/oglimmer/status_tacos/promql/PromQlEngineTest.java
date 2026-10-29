/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import de.oglimmer.status_tacos.promql.PromQlEngine.InstantVector;
import de.oglimmer.status_tacos.promql.PromQlEngine.Point;
import de.oglimmer.status_tacos.promql.PromQlEngine.RangeSeries;
import de.oglimmer.status_tacos.promql.PromQlEngine.Sample;
import de.oglimmer.status_tacos.promql.PromQlEngine.Scalar;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class PromQlEngineTest {

  private static final long MIN = 60_000;
  private static final long LOOKBACK = 5 * MIN;

  /** Shop: up at 0, 1, 2 min, down at 3 min. Blog: up at 0..3 min. */
  private final List<Series> data =
      List.of(
          series("probe_success", "Shop", "Prod", new double[] {1, 1, 1, 0}),
          series("probe_success", "Blog", "Dev", new double[] {1, 1, 1, 1}),
          series("probe_duration_seconds", "Shop", "Prod", new double[] {0.1, 0.2, 0.3, 0.6}));

  private final SeriesSource source =
      (matchers, from, to) ->
          data.stream()
              .filter(s -> matchers.stream().allMatch(m -> m.matches(s.labels())))
              .toList();

  @Test
  void selectorReturnsTheNewestSample() {
    InstantVector result = vector("probe_success{monitor=\"Shop\"}", 3 * MIN + 10_000);

    assertThat(result.samples()).hasSize(1);
    assertThat(result.samples().getFirst().value()).isZero();
    assertThat(result.samples().getFirst().labels()).containsEntry("__name__", "probe_success");
  }

  @Test
  void sampleOlderThanTheLookbackIsGone() {
    assertThat(vector("probe_success", 3 * MIN + LOOKBACK + 1).samples()).isEmpty();
    assertThat(vector("probe_success", -1).samples()).isEmpty();
  }

  @Test
  void overTimeFunctions() {
    long t = 3 * MIN;
    assertThat(value("avg_over_time(probe_success{monitor=\"Shop\"}[10m])", t)).isEqualTo(0.75);
    assertThat(value("min_over_time(probe_success{monitor=\"Shop\"}[10m])", t)).isZero();
    assertThat(value("count_over_time(probe_success{monitor=\"Shop\"}[10m])", t)).isEqualTo(4);
    // the window is (t - 2m, t]: the samples at 2 and 3 min
    assertThat(value("sum_over_time(probe_success{monitor=\"Shop\"}[2m])", t)).isEqualTo(1);
    assertThat(value("max_over_time(probe_duration_seconds[10m])", t)).isEqualTo(0.6);
    assertThat(value("last_over_time(probe_duration_seconds[10m])", 2 * MIN)).isEqualTo(0.3);
    assertThat(vector("avg_over_time(probe_success[10m])", t).samples().getFirst().labels())
        .doesNotContainKey("__name__");
  }

  @Test
  void aggregations() {
    long t = 3 * MIN;
    assertThat(value("avg(probe_success)", t)).isEqualTo(0.5);
    assertThat(value("count(probe_success)", t)).isEqualTo(2);

    InstantVector byTenant = vector("sum by (tenant) (probe_success)", t);
    assertThat(byTenant.samples())
        .extracting(Sample::labels, Sample::value)
        .containsExactlyInAnyOrder(
            tuple(new TreeMap<>(Map.of("tenant", "Prod")), 0.0),
            tuple(new TreeMap<>(Map.of("tenant", "Dev")), 1.0));

    InstantVector without = vector("max without (tenant, monitor) (probe_success)", t);
    assertThat(without.samples().getFirst().labels()).containsOnlyKeys("job");
  }

  @Test
  void arithmeticAndComparison() {
    long t = 3 * MIN;
    assertThat(value("probe_duration_seconds * 1000", t)).isEqualTo(600);
    assertThat(value("100 - probe_success{monitor=\"Blog\"} * 100", t)).isZero();

    // a comparison keeps the matching samples with their value and name
    InstantVector down = vector("probe_success == 0", t);
    assertThat(down.samples()).hasSize(1);
    assertThat(down.samples().getFirst().labels()).containsEntry("monitor", "Shop");

    InstantVector bool = vector("probe_success == bool 0", t);
    assertThat(bool.samples()).extracting(Sample::value).containsExactlyInAnyOrder(1.0, 0.0);
  }

  @Test
  void vectorToVectorMatchesOnLabelsWithoutName() {
    double v = value("probe_duration_seconds / probe_success{monitor=\"Shop\"}", 2 * MIN);
    assertThat(v).isEqualTo(0.3);
  }

  @Test
  void scalars() {
    assertThat(PromQlEngine.instant(PromQlParser.parse("1+1"), 0, source)).isEqualTo(new Scalar(2));
    assertThat(PromQlEngine.instant(PromQlParser.parse("2 ^ 3 ^ 2"), 0, source))
        .isEqualTo(new Scalar(512));
    assertThat(PromQlEngine.instant(PromQlParser.parse("time()"), 5_000, source))
        .isEqualTo(new Scalar(5));
    assertThat(value("vector(3)", 0)).isEqualTo(3);
    assertThat(PromQlEngine.instant(PromQlParser.parse("scalar(probe_success)"), 3 * MIN, source))
        .isEqualTo(new Scalar(Double.NaN));
  }

  @Test
  void otherFunctions() {
    long t = 3 * MIN;
    assertThat(value("round(probe_duration_seconds)", t)).isEqualTo(1);
    assertThat(value("round(probe_duration_seconds, 0.25)", t)).isEqualTo(0.5);
    assertThat(value("clamp_max(probe_duration_seconds, 0.5)", t)).isEqualTo(0.5);
    assertThat(value("abs(-1 * probe_duration_seconds)", t)).isEqualTo(0.6);
  }

  @Test
  void rangeQueryEvaluatesEachStep() {
    List<RangeSeries> result =
        PromQlEngine.range(
            PromQlParser.parse("probe_success{monitor=\"Shop\"}"), 0, 4 * MIN, MIN, source);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().points())
        .extracting(Point::value)
        .containsExactly(1.0, 1.0, 1.0, 0.0, 0.0);
  }

  @Test
  void rangeQueryOfScalar() {
    List<RangeSeries> result = PromQlEngine.range(PromQlParser.parse("1"), 0, MIN, MIN, source);

    assertThat(result.getFirst().labels()).isEmpty();
    assertThat(result.getFirst().points()).hasSize(2);
  }

  @Test
  void errors() {
    assertThatThrownBy(() -> vector("probe_success[5m]", 0))
        .isInstanceOf(PromQlException.class)
        .hasMessageContaining("avg_over_time");
    assertThatThrownBy(() -> vector("rate(probe_success[5m])", 0))
        .hasMessageContaining("not supported");
    assertThatThrownBy(() -> vector("avg_over_time(probe_success)", 0))
        .hasMessageContaining("range selector");
    assertThatThrownBy(
            () -> PromQlEngine.range(PromQlParser.parse("1"), 0, 20_000_000, 1_000, source))
        .hasMessageContaining("11,000");
    // without the name, success and duration of Shop have the same labels
    assertThatThrownBy(() -> vector("probe_success{monitor=\"Shop\"} + {monitor=\"Shop\"}", MIN))
        .hasMessageContaining("many-to-many");
  }

  private double value(String query, long t) {
    InstantVector result = vector(query, t);
    assertThat(result.samples()).hasSize(1);
    return result.samples().getFirst().value();
  }

  private InstantVector vector(String query, long t) {
    return (InstantVector) PromQlEngine.instant(PromQlParser.parse(query), t, source);
  }

  private static Series series(String name, String monitor, String tenant, double[] values) {
    SortedMap<String, String> labels = new TreeMap<>();
    labels.put("__name__", name);
    labels.put("job", "status-tacos");
    labels.put("monitor", monitor);
    labels.put("tenant", tenant);
    long[] times = new long[values.length];
    for (int i = 0; i < values.length; i++) {
      times[i] = i * MIN;
    }
    return new Series(labels, times, values, LOOKBACK);
  }
}
