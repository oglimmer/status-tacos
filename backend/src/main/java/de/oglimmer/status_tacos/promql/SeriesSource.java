/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import java.util.List;

/** The data a query reads. */
public interface SeriesSource {

  /**
   * The series that match all matchers, with their samples in [from - lookback, to]. The lookback
   * of each series is added by the source.
   */
  List<Series> select(List<LabelMatcher> matchers, long fromMillis, long toMillis);
}
