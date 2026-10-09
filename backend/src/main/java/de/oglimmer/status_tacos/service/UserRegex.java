/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The regexes of the monitors (status code, body, Prometheus key) are written by users.
 *
 * <p>Each regex is compiled once and kept. A match can take very long (catastrophic backtracking,
 * for example {@code (a+)+$}), and Java can not interrupt a running match. So the matched text is
 * wrapped: it throws {@link TimeoutException} when the regex reads it after the deadline.
 */
final class UserRegex {

  /** Thrown from inside the match when the regex runs past its deadline. */
  static final class TimeoutException extends RuntimeException {
    TimeoutException() {
      super(null, null, false, false);
    }
  }

  /** Guards against unbounded growth when users change their regexes often. */
  private static final int MAX_CACHED_PATTERNS = 1000;

  private final Map<String, Pattern> cache = new ConcurrentHashMap<>();
  private final Duration timeout;

  UserRegex(Duration timeout) {
    this.timeout = timeout;
  }

  Duration timeout() {
    return timeout;
  }

  /**
   * @throws PatternSyntaxException if the regex is invalid (invalid regexes are not cached)
   */
  Pattern compile(String regex, int flags) {
    String key = flags + ":" + regex;
    Pattern pattern = cache.get(key);
    if (pattern == null) {
      pattern = Pattern.compile(regex, flags);
      if (cache.size() >= MAX_CACHED_PATTERNS) {
        cache.clear();
      }
      cache.put(key, pattern);
    }
    return pattern;
  }

  /** The deadline for all matches of one check. */
  long deadline() {
    return System.nanoTime() + timeout.toNanos();
  }

  /** The text to match, which stops the match after the deadline. */
  static CharSequence withDeadline(CharSequence text, long deadlineNanos) {
    return new DeadlineCharSequence(text, deadlineNanos);
  }

  private static final class DeadlineCharSequence implements CharSequence {

    /** Reading the clock costs time, so it is read every 1024 characters only. */
    private static final int CLOCK_EVERY = 1024;

    private final CharSequence text;
    private final long deadlineNanos;
    private int reads;

    DeadlineCharSequence(CharSequence text, long deadlineNanos) {
      this.text = text;
      this.deadlineNanos = deadlineNanos;
    }

    @Override
    public char charAt(int index) {
      if (++reads % CLOCK_EVERY == 0 && System.nanoTime() > deadlineNanos) {
        throw new TimeoutException();
      }
      return text.charAt(index);
    }

    @Override
    public int length() {
      return text.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return new DeadlineCharSequence(text.subSequence(start, end), deadlineNanos);
    }

    @Override
    public String toString() {
      return text.toString();
    }
  }
}
