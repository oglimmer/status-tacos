/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * Reads the Prometheus text format: {@code name{label="value",...} value [timestamp]}.
 *
 * <p>Label values can hold spaces, braces and escaped quotes. The optional timestamp is ignored.
 */
final class PrometheusParser {

  private PrometheusParser() {}

  /**
   * Sums the values of all samples whose series (name and labels, as written) matches. NaN values
   * are skipped.
   *
   * @return empty if no sample matches
   */
  static OptionalDouble sumMatching(String text, Predicate<String> seriesMatches) {
    double total = 0.0;
    boolean found = false;
    for (String rawLine : text.split("\n")) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      int end = seriesEnd(line);
      if (end < 0) {
        continue;
      }
      if (!seriesMatches.test(line.substring(0, end))) {
        continue;
      }
      OptionalDouble value = parseValue(line.substring(end).trim());
      if (value.isPresent()) {
        total += value.getAsDouble();
        found = true;
      }
    }
    return found ? OptionalDouble.of(total) : OptionalDouble.empty();
  }

  /** End (exclusive) of the series part of a sample line, or -1 if the line has no value. */
  static int seriesEnd(String line) {
    boolean inLabels = false;
    boolean inQuotes = false;
    boolean escaped = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuotes) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inQuotes = false;
        }
      } else if (inLabels) {
        if (c == '"') {
          inQuotes = true;
        } else if (c == '}') {
          return i + 1 < line.length() ? i + 1 : -1;
        }
      } else if (c == '{') {
        inLabels = true;
      } else if (Character.isWhitespace(c)) {
        return i;
      }
    }
    return -1;
  }

  /** The first token of the rest of the line. Accepts the Prometheus forms +Inf, -Inf and NaN. */
  static OptionalDouble parseValue(String rest) {
    if (rest.isEmpty()) {
      return OptionalDouble.empty();
    }
    String token = rest.split("\\s+", 2)[0];
    double value;
    switch (token) {
      case "+Inf", "Inf" -> value = Double.POSITIVE_INFINITY;
      case "-Inf" -> value = Double.NEGATIVE_INFINITY;
      default -> {
        try {
          value = Double.parseDouble(token);
        } catch (NumberFormatException e) {
          return OptionalDouble.empty();
        }
      }
    }
    return Double.isNaN(value) ? OptionalDouble.empty() : OptionalDouble.of(value);
  }
}
