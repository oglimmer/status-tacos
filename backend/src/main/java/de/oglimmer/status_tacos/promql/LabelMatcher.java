/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** One matcher of a selector, for example {@code monitor=~"shop.*"}. */
public record LabelMatcher(String name, Type type, String value, Pattern pattern) {

  public enum Type {
    EQ("="),
    NEQ("!="),
    RE("=~"),
    NRE("!~");

    private final String op;

    Type(String op) {
      this.op = op;
    }

    @Override
    public String toString() {
      return op;
    }
  }

  public static LabelMatcher of(String name, Type type, String value) {
    Pattern pattern = null;
    if (type == Type.RE || type == Type.NRE) {
      try {
        // Prometheus regexes match the full value.
        pattern = Pattern.compile("(?:" + value + ")");
      } catch (PatternSyntaxException e) {
        throw new PromQlException("invalid regular expression \"" + value + "\"");
      }
    }
    return new LabelMatcher(name, type, value, pattern);
  }

  /** A missing label has the value "". */
  public boolean matches(Map<String, String> labels) {
    return matches(labels.getOrDefault(name, ""));
  }

  public boolean matches(String actual) {
    return switch (type) {
      case EQ -> actual.equals(value);
      case NEQ -> !actual.equals(value);
      case RE -> pattern.matcher(actual).matches();
      case NRE -> !pattern.matcher(actual).matches();
    };
  }
}
