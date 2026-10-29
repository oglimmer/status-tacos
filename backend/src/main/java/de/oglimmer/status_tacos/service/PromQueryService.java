/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.promql.Expr;
import de.oglimmer.status_tacos.promql.LabelMatcher;
import de.oglimmer.status_tacos.promql.PromQlEngine;
import de.oglimmer.status_tacos.promql.PromQlException;
import de.oglimmer.status_tacos.promql.PromQlParser;
import de.oglimmer.status_tacos.promql.Series;
import de.oglimmer.status_tacos.promql.SeriesSource;
import de.oglimmer.status_tacos.repository.CheckRollupRepository;
import de.oglimmer.status_tacos.repository.CheckRollupRepository.HourRow;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.ProbeSampleRepository;
import de.oglimmer.status_tacos.repository.ProbeSampleRepository.ProbeSample;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The data of the Prometheus query API: one series per monitor and {@link ProbeMetric}, from the
 * monitors of the tenants of the user.
 */
@Service
@RequiredArgsConstructor
public class PromQueryService {

  /** Raw check results one query may load. About 80 MB of memory. */
  static final int MAX_RAW_SAMPLES = 1_000_000;

  static final long MIN_LOOKBACK_MILLIS = 5 * 60_000L;
  static final long HOUR_MILLIS = 3_600_000L;

  /** An hour is rolled up after it ends, so the newest hourly sample can be up to 2 h old. */
  static final long HOURLY_LOOKBACK_MILLIS = 2 * HOUR_MILLIS;

  static final String JOB = "status-tacos";

  private final MonitorRepository monitorRepository;
  private final ProbeSampleRepository probeSampleRepository;
  private final CheckRollupRepository checkRollupRepository;

  /** One series that exists: a metric of a monitor, with its labels. */
  private record Entry(ProbeMetric metric, Monitor monitor, SortedMap<String, String> labels) {}

  public PromQlEngine.Value instantQuery(Set<Integer> tenantIds, String query, long timeMillis) {
    Expr expr = PromQlParser.parse(query);
    return PromQlEngine.instant(expr, timeMillis, new Source(catalog(tenantIds)));
  }

  public List<PromQlEngine.RangeSeries> rangeQuery(
      Set<Integer> tenantIds, String query, long startMillis, long endMillis, long stepMillis) {
    Expr expr = PromQlParser.parse(query);
    return PromQlEngine.range(
        expr, startMillis, endMillis, stepMillis, new Source(catalog(tenantIds)));
  }

  /** The label sets of the series that match one of the selectors, or of all series. */
  public List<SortedMap<String, String>> series(Set<Integer> tenantIds, List<String> match) {
    return matching(tenantIds, match).stream().map(Entry::labels).toList();
  }

  public List<String> labelNames(Set<Integer> tenantIds, List<String> match) {
    return matching(tenantIds, match).stream()
        .flatMap(entry -> entry.labels().keySet().stream())
        .collect(Collectors.toCollection(TreeSet::new))
        .stream()
        .toList();
  }

  public List<String> labelValues(Set<Integer> tenantIds, String label, List<String> match) {
    return matching(tenantIds, match).stream()
        .map(entry -> entry.labels().get(label))
        .filter(value -> value != null && !value.isEmpty())
        .collect(Collectors.toCollection(TreeSet::new))
        .stream()
        .toList();
  }

  /** Metric name to its type and help text, as GET /api/v1/metadata returns it. */
  public Map<String, List<Map<String, String>>> metadata() {
    Map<String, List<Map<String, String>>> metadata = new LinkedHashMap<>();
    for (ProbeMetric metric : ProbeMetric.values()) {
      metadata.put(
          metric.metricName(), List.of(Map.of("type", "gauge", "help", metric.help(), "unit", "")));
    }
    return metadata;
  }

  // --- series catalog ---

  private List<Entry> catalog(Set<Integer> tenantIds) {
    if (tenantIds.isEmpty()) {
      return List.of();
    }
    List<Entry> entries = new ArrayList<>();
    for (Monitor monitor : monitorRepository.findByTenantIdIn(tenantIds)) {
      for (ProbeMetric metric : ProbeMetric.values()) {
        entries.add(new Entry(metric, monitor, labels(metric, monitor)));
      }
    }
    return entries;
  }

  private static SortedMap<String, String> labels(ProbeMetric metric, Monitor monitor) {
    SortedMap<String, String> labels = new TreeMap<>();
    labels.put("__name__", metric.metricName());
    labels.put("job", JOB);
    labels.put("monitor", monitor.getName());
    labels.put("monitor_id", String.valueOf(monitor.getId()));
    labels.put("instance", monitor.getUrl());
    labels.put("tenant", monitor.getTenant().getName());
    return labels;
  }

  private List<Entry> matching(Set<Integer> tenantIds, List<String> match) {
    List<Entry> catalog = catalog(tenantIds);
    if (match == null || match.isEmpty()) {
      return catalog;
    }
    List<List<LabelMatcher>> selectors = new ArrayList<>();
    for (String selector : match) {
      if (!(PromQlParser.parse(selector) instanceof Expr.VectorSelector vector)) {
        throw new PromQlException("match[] must be a series selector, got \"" + selector + "\"");
      }
      selectors.add(vector.matchers());
    }
    return catalog.stream()
        .filter(entry -> selectors.stream().anyMatch(m -> matchesAll(m, entry.labels())))
        .toList();
  }

  private static boolean matchesAll(List<LabelMatcher> matchers, Map<String, String> labels) {
    return matchers.stream().allMatch(matcher -> matcher.matches(labels));
  }

  private static long lookback(Entry entry) {
    return entry.metric().raw()
        ? Math.max(MIN_LOOKBACK_MILLIS, 2_000L * entry.monitor().getCheckInterval())
        : HOURLY_LOOKBACK_MILLIS;
  }

  private static LocalDateTime toDateTime(long epochMillis) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
  }

  /** Loads the samples of one query. The raw sample budget is shared by all its selectors. */
  private class Source implements SeriesSource {

    private final List<Entry> catalog;
    private int rawBudget = MAX_RAW_SAMPLES;

    Source(List<Entry> catalog) {
      this.catalog = catalog;
    }

    @Override
    public List<Series> select(List<LabelMatcher> matchers, long fromMillis, long toMillis) {
      List<Entry> raw = new ArrayList<>();
      List<Entry> hourly = new ArrayList<>();
      for (Entry entry : catalog) {
        if (matchesAll(matchers, entry.labels())) {
          (entry.metric().raw() ? raw : hourly).add(entry);
        }
      }
      List<Series> series = new ArrayList<>();
      if (!raw.isEmpty()) {
        series.addAll(rawSeries(raw, fromMillis, toMillis));
      }
      if (!hourly.isEmpty()) {
        series.addAll(hourlySeries(hourly, fromMillis, toMillis));
      }
      return series;
    }

    private List<Series> rawSeries(List<Entry> entries, long fromMillis, long toMillis) {
      Set<Integer> monitorIds = monitorIds(entries);
      long maxLookback = entries.stream().mapToLong(PromQueryService::lookback).max().orElse(0);
      List<ProbeSample> rows =
          probeSampleRepository.findSamples(
              monitorIds,
              toDateTime(fromMillis - maxLookback),
              toDateTime(toMillis),
              rawBudget + 1);
      if (rows.size() > rawBudget) {
        throw new PromQlException(
            "execution",
            "the query needs more than "
                + MAX_RAW_SAMPLES
                + " check results. Use a shorter time range, fewer monitors or the"
                + " tacos_*_1h metrics.");
      }
      rawBudget -= rows.size();
      Map<Integer, List<ProbeSample>> byMonitor =
          rows.stream().collect(Collectors.groupingBy(ProbeSample::monitorId));

      List<Series> series = new ArrayList<>();
      for (Entry entry : entries) {
        Function<ProbeSample, Double> value =
            switch (entry.metric()) {
              case PROBE_SUCCESS -> sample -> sample.up() ? 1.0 : 0.0;
              case PROBE_DURATION_SECONDS ->
                  sample ->
                      sample.responseTimeMs() == null ? null : sample.responseTimeMs() / 1000.0;
              case PROBE_HTTP_STATUS_CODE ->
                  sample -> sample.statusCode() == null ? null : sample.statusCode().doubleValue();
              default -> throw new IllegalStateException("not a raw metric: " + entry.metric());
            };
        long lookback = lookback(entry);
        List<Long> times = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (ProbeSample sample : byMonitor.getOrDefault(entry.monitor().getId(), List.of())) {
          Double v = value.apply(sample);
          if (v != null && sample.timeMillis() >= fromMillis - lookback) {
            times.add(sample.timeMillis());
            values.add(v);
          }
        }
        addSeries(series, entry, times, values, lookback);
      }
      return series;
    }

    /** One sample per hour, at the end of the hour. */
    private List<Series> hourlySeries(List<Entry> entries, long fromMillis, long toMillis) {
      long firstBucket = fromMillis - HOURLY_LOOKBACK_MILLIS - HOUR_MILLIS;
      long lastBucket = toMillis - HOUR_MILLIS;
      if (lastBucket < firstBucket) {
        return List.of();
      }
      Map<Integer, List<HourRow>> byMonitor =
          checkRollupRepository
              .findHourly(monitorIds(entries), toDateTime(firstBucket), toDateTime(lastBucket + 1))
              .stream()
              .sorted(Comparator.comparing(HourRow::bucketStart))
              .collect(Collectors.groupingBy(HourRow::monitorId));

      List<Series> series = new ArrayList<>();
      for (Entry entry : entries) {
        Function<HourRow, Double> value =
            switch (entry.metric()) {
              case UPTIME_RATIO_1H ->
                  row ->
                      row.totalSeconds() == 0
                          ? null
                          : (double) row.upSeconds() / row.totalSeconds();
              case DURATION_SECONDS_AVG_1H ->
                  row -> row.rtCount() == 0 ? null : row.rtSum() / 1000.0 / row.rtCount();
              case DURATION_SECONDS_MAX_1H ->
                  row -> row.rtMax() == null ? null : row.rtMax() / 1000.0;
              default -> throw new IllegalStateException("not an hourly metric: " + entry.metric());
            };
        List<Long> times = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (HourRow row : byMonitor.getOrDefault(entry.monitor().getId(), List.of())) {
          Double v = value.apply(row);
          if (v != null) {
            times.add(row.bucketStart().toInstant(ZoneOffset.UTC).toEpochMilli() + HOUR_MILLIS);
            values.add(v);
          }
        }
        addSeries(series, entry, times, values, HOURLY_LOOKBACK_MILLIS);
      }
      return series;
    }

    private static void addSeries(
        List<Series> series, Entry entry, List<Long> times, List<Double> values, long lookback) {
      if (times.isEmpty()) {
        return;
      }
      series.add(
          new Series(
              entry.labels(),
              times.stream().mapToLong(Long::longValue).toArray(),
              values.stream().mapToDouble(Double::doubleValue).toArray(),
              lookback));
    }

    private static Set<Integer> monitorIds(List<Entry> entries) {
      return entries.stream()
          .map(entry -> entry.monitor().getId())
          .collect(Collectors.toCollection(TreeSet::new));
    }
  }
}
