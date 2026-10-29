/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import java.util.SortedMap;

/**
 * Samples of one time series, oldest first.
 *
 * @param lookbackMillis an instant query at time t uses the newest sample in (t - lookback, t]
 */
public record Series(
    SortedMap<String, String> labels, long[] times, double[] values, long lookbackMillis) {}
