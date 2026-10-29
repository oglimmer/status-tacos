/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import de.oglimmer.status_tacos.promql.Expr.Aggregation;
import de.oglimmer.status_tacos.promql.Expr.Binary;
import de.oglimmer.status_tacos.promql.Expr.Call;
import de.oglimmer.status_tacos.promql.Expr.MatrixSelector;
import de.oglimmer.status_tacos.promql.Expr.NumberLiteral;
import de.oglimmer.status_tacos.promql.Expr.VectorSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.DoubleUnaryOperator;

/**
 * Runs a parsed query against a {@link SeriesSource}, like Prometheus: a range query evaluates the
 * expression once per step.
 */
public final class PromQlEngine {

  /** The same limit as Prometheus. */
  public static final int MAX_POINTS_PER_SERIES = 11_000;

  private static final String NAME = "__name__";

  private static final List<String> FUNCTIONS =
      List.of(
          "avg_over_time",
          "min_over_time",
          "max_over_time",
          "sum_over_time",
          "count_over_time",
          "last_over_time",
          "abs",
          "ceil",
          "floor",
          "round",
          "clamp_min",
          "clamp_max",
          "scalar",
          "vector",
          "time");

  public sealed interface Value permits Scalar, InstantVector {}

  public record Scalar(double value) implements Value {}

  public record Sample(SortedMap<String, String> labels, double value) {}

  public record InstantVector(List<Sample> samples) implements Value {}

  public record Point(long timeMillis, double value) {}

  public record RangeSeries(SortedMap<String, String> labels, List<Point> points) {}

  /** The samples of one series in the window of a range selector. */
  private record Window(SortedMap<String, String> labels, double[] values) {}

  private record RangeVector(List<Window> windows) {}

  private final SeriesSource source;
  private final long start;
  private final Map<VectorSelector, Long> ranges = new HashMap<>();
  private final long end;
  private final Map<VectorSelector, List<Series>> loaded = new HashMap<>();

  private PromQlEngine(SeriesSource source, Expr expr, long start, long end) {
    this.source = source;
    this.start = start;
    this.end = end;
    collectRanges(expr);
  }

  public static Value instant(Expr expr, long time, SeriesSource source) {
    PromQlEngine engine = new PromQlEngine(source, expr, time, time);
    return engine.value(engine.eval(expr, time));
  }

  public static List<RangeSeries> range(
      Expr expr, long start, long end, long step, SeriesSource source) {
    if (step <= 0) {
      throw new PromQlException(
          "zero or negative query resolution step widths are not accepted. Try a positive integer");
    }
    if (end < start) {
      throw new PromQlException("end timestamp must not be before start time");
    }
    if ((end - start) / step + 1 > MAX_POINTS_PER_SERIES) {
      throw new PromQlException(
          "exceeded maximum resolution of 11,000 points per timeseries. Try decreasing the query"
              + " resolution (?step=XX)");
    }
    PromQlEngine engine = new PromQlEngine(source, expr, start, end);
    Map<SortedMap<String, String>, List<Point>> result = new LinkedHashMap<>();
    for (long t = start; t <= end; t += step) {
      Value value = engine.value(engine.eval(expr, t));
      List<Sample> samples =
          value instanceof Scalar scalar
              ? List.of(new Sample(new TreeMap<>(), scalar.value()))
              : ((InstantVector) value).samples();
      for (Sample sample : samples) {
        List<Point> points = result.computeIfAbsent(sample.labels(), k -> new ArrayList<>());
        if (!points.isEmpty() && points.getLast().timeMillis() == t) {
          throw new PromQlException(
              "execution", "vector cannot contain metrics with the same labelset");
        }
        points.add(new Point(t, sample.value()));
      }
    }
    return result.entrySet().stream()
        .map(entry -> new RangeSeries(entry.getKey(), entry.getValue()))
        .sorted((a, b) -> a.labels().toString().compareTo(b.labels().toString()))
        .toList();
  }

  // --- data ---

  /** The widest range of each selector, so each selector loads its data once. */
  private void collectRanges(Expr expr) {
    switch (expr) {
      case VectorSelector selector -> ranges.merge(selector, 0L, Math::max);
      case MatrixSelector matrix ->
          ranges.merge(matrix.selector(), matrix.rangeMillis(), Math::max);
      case Call call -> call.args().forEach(this::collectRanges);
      case Aggregation aggregation -> collectRanges(aggregation.expr());
      case Binary binary -> {
        collectRanges(binary.lhs());
        collectRanges(binary.rhs());
      }
      case NumberLiteral number -> {
        // no data
      }
    }
  }

  private List<Series> series(VectorSelector selector) {
    return loaded.computeIfAbsent(
        selector, s -> source.select(s.matchers(), start - ranges.getOrDefault(s, 0L), end));
  }

  // --- evaluation ---

  private Value value(Object result) {
    if (result instanceof RangeVector) {
      throw new PromQlException(
          "a range selector like metric[5m] only works inside a function like avg_over_time");
    }
    return (Value) result;
  }

  private Object eval(Expr expr, long t) {
    return switch (expr) {
      case NumberLiteral number -> new Scalar(number.value());
      case VectorSelector selector -> selectInstant(selector, t);
      case MatrixSelector matrix -> selectRange(matrix, t);
      case Call call -> call(call, t);
      case Aggregation aggregation -> aggregate(aggregation, t);
      case Binary binary -> binary(binary, t);
    };
  }

  private InstantVector selectInstant(VectorSelector selector, long t) {
    List<Sample> samples = new ArrayList<>();
    for (Series series : series(selector)) {
      int index = lastIndexAtOrBefore(series.times(), t);
      if (index >= 0 && series.times()[index] > t - series.lookbackMillis()) {
        samples.add(new Sample(series.labels(), series.values()[index]));
      }
    }
    return new InstantVector(samples);
  }

  /** Samples in (t - range, t]. */
  private RangeVector selectRange(MatrixSelector matrix, long t) {
    List<Window> windows = new ArrayList<>();
    for (Series series : series(matrix.selector())) {
      int to = lastIndexAtOrBefore(series.times(), t) + 1;
      int from = lastIndexAtOrBefore(series.times(), t - matrix.rangeMillis()) + 1;
      if (to > from) {
        windows.add(new Window(series.labels(), Arrays.copyOfRange(series.values(), from, to)));
      }
    }
    return new RangeVector(windows);
  }

  private static int lastIndexAtOrBefore(long[] times, long t) {
    int index = Arrays.binarySearch(times, t);
    if (index < 0) {
      return -index - 2;
    }
    // equal times: take the last one
    while (index + 1 < times.length && times[index + 1] == t) {
      index++;
    }
    return index;
  }

  private Object call(Call call, long t) {
    String name = call.function();
    List<Expr> args = call.args();
    return switch (name) {
      case "avg_over_time",
          "min_over_time",
          "max_over_time",
          "sum_over_time",
          "count_over_time",
          "last_over_time" -> {
        checkArgs(call, 1);
        if (!(eval(args.getFirst(), t) instanceof RangeVector range)) {
          throw new PromQlException(name + "() needs a range selector like metric[5m]");
        }
        List<Sample> samples = new ArrayList<>();
        for (Window window : range.windows()) {
          samples.add(new Sample(withoutName(window.labels()), overTime(name, window.values())));
        }
        yield new InstantVector(samples);
      }
      case "abs" -> map(call, t, Math::abs);
      case "ceil" -> map(call, t, Math::ceil);
      case "floor" -> map(call, t, Math::floor);
      case "round" -> {
        if (args.size() == 2) {
          double nearest = scalar(eval(args.get(1), t), name);
          yield mapVector(vector(eval(args.getFirst(), t), name), v -> roundTo(v, nearest));
        }
        yield map(call, t, v -> roundTo(v, 1));
      }
      case "clamp_min", "clamp_max" -> {
        checkArgs(call, 2);
        double limit = scalar(eval(args.get(1), t), name);
        DoubleUnaryOperator clamp =
            name.equals("clamp_min") ? v -> Math.max(v, limit) : v -> Math.min(v, limit);
        yield mapVector(vector(eval(args.getFirst(), t), name), clamp);
      }
      case "scalar" -> {
        checkArgs(call, 1);
        List<Sample> samples = vector(eval(args.getFirst(), t), name).samples();
        yield new Scalar(samples.size() == 1 ? samples.getFirst().value() : Double.NaN);
      }
      case "vector" -> {
        checkArgs(call, 1);
        yield new InstantVector(
            List.of(new Sample(new TreeMap<>(), scalar(eval(args.getFirst(), t), name))));
      }
      case "time" -> {
        checkArgs(call, 0);
        yield new Scalar(t / 1000.0);
      }
      default ->
          throw new PromQlException(
              "function \"" + name + "\" is not supported. Supported: " + FUNCTIONS);
    };
  }

  private static double overTime(String function, double[] values) {
    return switch (function) {
      case "avg_over_time" -> Arrays.stream(values).average().orElse(Double.NaN);
      case "min_over_time" -> Arrays.stream(values).min().orElse(Double.NaN);
      case "max_over_time" -> Arrays.stream(values).max().orElse(Double.NaN);
      case "sum_over_time" -> Arrays.stream(values).sum();
      case "count_over_time" -> values.length;
      default -> values[values.length - 1];
    };
  }

  private static double roundTo(double value, double nearest) {
    return Math.floor(value / nearest + 0.5) * nearest;
  }

  private InstantVector map(Call call, long t, DoubleUnaryOperator operator) {
    checkArgs(call, 1);
    return mapVector(vector(eval(call.args().getFirst(), t), call.function()), operator);
  }

  private static InstantVector mapVector(InstantVector vector, DoubleUnaryOperator operator) {
    return new InstantVector(
        vector.samples().stream()
            .map(s -> new Sample(withoutName(s.labels()), operator.applyAsDouble(s.value())))
            .toList());
  }

  private static void checkArgs(Call call, int count) {
    if (call.args().size() != count) {
      throw new PromQlException(
          call.function() + "() needs " + count + " argument(s), got " + call.args().size());
    }
  }

  private Object aggregate(Aggregation aggregation, long t) {
    InstantVector input = vector(eval(aggregation.expr(), t), aggregation.op());
    Set<String> grouping = new HashSet<>(aggregation.grouping());
    Map<SortedMap<String, String>, List<Double>> groups = new LinkedHashMap<>();
    for (Sample sample : input.samples()) {
      SortedMap<String, String> key = new TreeMap<>();
      sample
          .labels()
          .forEach(
              (label, value) -> {
                boolean listed = grouping.contains(label);
                boolean keep = aggregation.without() ? !listed && !label.equals(NAME) : listed;
                if (keep) {
                  key.put(label, value);
                }
              });
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(sample.value());
    }
    List<Sample> result = new ArrayList<>();
    groups.forEach(
        (labels, values) -> {
          double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
          double value =
              switch (aggregation.op()) {
                case "sum" -> Arrays.stream(array).sum();
                case "avg" -> Arrays.stream(array).average().orElse(Double.NaN);
                case "min" -> Arrays.stream(array).min().orElse(Double.NaN);
                case "max" -> Arrays.stream(array).max().orElse(Double.NaN);
                default -> array.length;
              };
          result.add(new Sample(labels, value));
        });
    return new InstantVector(result);
  }

  private Object binary(Binary binary, long t) {
    Value lhs = value(eval(binary.lhs(), t));
    Value rhs = value(eval(binary.rhs(), t));
    String op = binary.op();
    boolean comparison = isComparison(op);

    if (lhs instanceof Scalar a && rhs instanceof Scalar b) {
      return new Scalar(
          comparison ? (compare(op, a.value(), b.value()) ? 1 : 0) : arithmetic(op, a, b));
    }
    if (lhs instanceof InstantVector a && rhs instanceof InstantVector b) {
      return vectorVector(binary, a, b);
    }
    boolean scalarLeft = lhs instanceof Scalar;
    double scalar = scalarLeft ? ((Scalar) lhs).value() : ((Scalar) rhs).value();
    InstantVector vector = (InstantVector) (scalarLeft ? rhs : lhs);
    List<Sample> result = new ArrayList<>();
    for (Sample sample : vector.samples()) {
      double left = scalarLeft ? scalar : sample.value();
      double right = scalarLeft ? sample.value() : scalar;
      Sample out = combine(binary, sample, left, right);
      if (out != null) {
        result.add(out);
      }
    }
    return new InstantVector(result);
  }

  /** One-to-one matching on all labels but the metric name. */
  private InstantVector vectorVector(Binary binary, InstantVector lhs, InstantVector rhs) {
    Map<SortedMap<String, String>, Sample> right = new HashMap<>();
    for (Sample sample : rhs.samples()) {
      if (right.put(withoutName(sample.labels()), sample) != null) {
        throw new PromQlException(
            "execution",
            "found duplicate series for the match group on the right hand-side of the operation;"
                + " many-to-many matching not allowed");
      }
    }
    List<Sample> result = new ArrayList<>();
    for (Sample sample : lhs.samples()) {
      Sample match = right.get(withoutName(sample.labels()));
      if (match != null) {
        Sample out = combine(binary, sample, sample.value(), match.value());
        if (out != null) {
          result.add(out);
        }
      }
    }
    return new InstantVector(result);
  }

  /**
   * Arithmetic drops the metric name. A comparison keeps or drops the sample of the vector, or with
   * "bool" gives 0 or 1.
   */
  private static Sample combine(Binary binary, Sample sample, double left, double right) {
    String op = binary.op();
    if (!isComparison(op)) {
      return new Sample(withoutName(sample.labels()), arithmetic(op, left, right));
    }
    boolean result = compare(op, left, right);
    if (binary.returnBool()) {
      return new Sample(withoutName(sample.labels()), result ? 1 : 0);
    }
    return result ? sample : null;
  }

  private static boolean isComparison(String op) {
    return switch (op) {
      case "==", "!=", ">", "<", ">=", "<=" -> true;
      default -> false;
    };
  }

  private static double arithmetic(String op, Scalar a, Scalar b) {
    return arithmetic(op, a.value(), b.value());
  }

  private static double arithmetic(String op, double a, double b) {
    return switch (op) {
      case "+" -> a + b;
      case "-" -> a - b;
      case "*" -> a * b;
      case "/" -> a / b;
      case "%" -> a % b;
      default -> Math.pow(a, b);
    };
  }

  private static boolean compare(String op, double a, double b) {
    return switch (op) {
      case "==" -> a == b;
      case "!=" -> a != b;
      case ">" -> a > b;
      case "<" -> a < b;
      case ">=" -> a >= b;
      default -> a <= b;
    };
  }

  private static InstantVector vector(Object value, String context) {
    if (value instanceof InstantVector vector) {
      return vector;
    }
    throw new PromQlException(context + " needs an instant vector, like a metric selector");
  }

  private static double scalar(Object value, String context) {
    if (value instanceof Scalar scalar) {
      return scalar.value();
    }
    throw new PromQlException(context + " needs a number");
  }

  private static SortedMap<String, String> withoutName(SortedMap<String, String> labels) {
    if (!labels.containsKey(NAME)) {
      return labels;
    }
    SortedMap<String, String> copy = new TreeMap<>(labels);
    copy.remove(NAME);
    return copy;
  }
}
