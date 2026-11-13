/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.stream.Collectors;

/** The environment of the system that a monitor checks. Only a label, it changes no check. */
public enum MonitorEnvironment {
  DEV,
  STAGE,
  PROD;

  /** "DEV, STAGE, PROD", for error messages. */
  public static final String ALLOWED_VALUES =
      Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));

  /**
   * JSON value to enum. Null or an empty text gives null (not set). Other values must be one of the
   * names in upper case, else IllegalArgumentException, which the request handling makes a 400.
   */
  @JsonCreator
  public static MonitorEnvironment fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (MonitorEnvironment environment : values()) {
      if (environment.name().equals(value)) {
        return environment;
      }
    }
    throw new IllegalArgumentException(
        "Environment must be one of " + ALLOWED_VALUES + " or null, not: " + value);
  }
}
