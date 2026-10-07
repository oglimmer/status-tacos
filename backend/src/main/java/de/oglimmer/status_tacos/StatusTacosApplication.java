/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StatusTacosApplication {

  public static void main(String[] args) {
    // Check times are stored as UTC wall-clock time. The uptime stats align their buckets to UTC
    // hours and days, so the JVM must not use a local time zone.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(StatusTacosApplication.class, args);
  }
}
