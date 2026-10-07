/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ResponseTimeHistogramTest {

  @Test
  void empty_noPercentile() {
    assertThat(new ResponseTimeHistogram().percentile(99, null, null)).isNull();
  }

  @Test
  void bucketOf_isLogScale() {
    assertThat(ResponseTimeHistogram.bucketOf(0)).isZero();
    assertThat(ResponseTimeHistogram.bucketOf(1)).isZero();
    assertThat(ResponseTimeHistogram.bucketOf(100)).isEqualTo(94);
    assertThat(ResponseTimeHistogram.bucketOf(105)).isEqualTo(95);
  }

  @Test
  void singleValue_isClampedToExactValue() {
    ResponseTimeHistogram histogram = new ResponseTimeHistogram();
    histogram.add(ResponseTimeHistogram.bucketOf(250), 10);

    assertThat(histogram.percentile(99, 250, 250)).isEqualTo(250);
  }

  @Test
  void p99_isWithinTwoAndAHalfPercentOfExactValue() {
    Random random = new Random(42);
    int[] values = new int[20_000];
    ResponseTimeHistogram histogram = new ResponseTimeHistogram();
    for (int i = 0; i < values.length; i++) {
      values[i] = 20 + (int) Math.abs(random.nextGaussian() * 400);
      histogram.add(ResponseTimeHistogram.bucketOf(values[i]), 1);
    }
    Arrays.sort(values);
    int exact = values[(int) Math.ceil(0.99 * values.length) - 1];

    Integer estimate = histogram.percentile(99, values[0], values[values.length - 1]);

    assertThat((double) estimate).isCloseTo(exact, within(exact * 0.025));
  }

  @Test
  void histogramsCanBeAdded() {
    ResponseTimeHistogram histogram = new ResponseTimeHistogram();
    histogram.add(10, 5);
    histogram.add(10, 3);
    histogram.add(20, 2);

    assertThat(histogram.totalCount()).isEqualTo(10);
  }
}
