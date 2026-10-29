/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import lombok.Getter;

/** A query that cannot run. The error type is the one of the Prometheus HTTP API. */
@Getter
public class PromQlException extends RuntimeException {

  /** "bad_data": the query is wrong. "execution": the query is too big. */
  private final String errorType;

  public PromQlException(String message) {
    this("bad_data", message);
  }

  public PromQlException(String errorType, String message) {
    super(message);
    this.errorType = errorType;
  }
}
