/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.util.Map;
import java.util.TreeMap;

/**
 * Log-scale histogram of response times. Bucket {@code b} holds the values in [1.05^b, 1.05^(b+1)).
 * Histograms of hours and days can be added up, so a percentile over 90 days needs no raw check
 * results.
 *
 * <p>The SQL of {@link de.oglimmer.status_tacos.repository.CheckRollupRepository} computes the same
 * bucket with {@code FLOOR(LN(GREATEST(response_time_ms, 1)) / LN(1.05))}.
 */
public final class ResponseTimeHistogram {

  static final double BASE = 1.05;

  private final TreeMap<Integer, Long> counts = new TreeMap<>();

  public static int bucketOf(int responseTimeMs) {
    return (int) Math.floor(Math.log(Math.max(responseTimeMs, 1)) / Math.log(BASE));
  }

  public void add(int bucket, long count) {
    counts.merge(bucket, count, Long::sum);
  }

  public long totalCount() {
    return counts.values().stream().mapToLong(Long::longValue).sum();
  }

  /**
   * Nearest-rank percentile. The result is the geometric middle of the bucket, so the error is at
   * most about 2.5 %. It is clamped to the exact min and max, when they are known.
   *
   * @return null if the histogram is empty
   */
  public Integer percentile(int percentile, Integer min, Integer max) {
    long total = totalCount();
    if (total == 0) {
      return null;
    }
    long rank = Math.max(1, (long) Math.ceil(percentile / 100.0 * total));
    long seen = 0;
    int bucket = counts.lastKey();
    for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
      seen += entry.getValue();
      if (seen >= rank) {
        bucket = entry.getKey();
        break;
      }
    }
    long value = Math.round(Math.pow(BASE, bucket + 0.5));
    if (min != null) {
      value = Math.max(value, min);
    }
    if (max != null) {
      value = Math.min(value, max);
    }
    return (int) value;
  }
}
