/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import java.util.List;

/** The syntax tree of a PromQL query. */
public sealed interface Expr {

  record NumberLiteral(double value) implements Expr {}

  /** {@code metric{label="value"}}. The metric name is a matcher on {@code __name__}. */
  record VectorSelector(List<LabelMatcher> matchers) implements Expr {}

  /** {@code metric[5m]}. */
  record MatrixSelector(VectorSelector selector, long rangeMillis) implements Expr {}

  record Call(String function, List<Expr> args) implements Expr {}

  /** {@code sum by (monitor) (expr)}. */
  record Aggregation(String op, Expr expr, List<String> grouping, boolean without)
      implements Expr {}

  /** {@code a + b}, {@code a > bool b}. */
  record Binary(String op, Expr lhs, Expr rhs, boolean returnBool) implements Expr {}
}
