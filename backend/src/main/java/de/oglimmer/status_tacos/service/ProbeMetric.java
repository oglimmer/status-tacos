/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

/**
 * The metrics of the Prometheus query API. The probe_* names are the ones of the Prometheus
 * blackbox_exporter, so its dashboards work with few changes.
 */
public enum ProbeMetric {
  PROBE_SUCCESS("probe_success", true, "1 if the check was successful, 0 if not. One per check."),
  PROBE_DURATION_SECONDS(
      "probe_duration_seconds", true, "Response time of the check in seconds. One per check."),
  PROBE_HTTP_STATUS_CODE(
      "probe_http_status_code", true, "HTTP status code of the check. One per check."),
  UPTIME_RATIO_1H(
      "tacos_uptime_ratio_1h",
      false,
      "Uptime (0 to 1) of the hour that ends at the sample time. Each check counts with its check"
          + " interval."),
  DURATION_SECONDS_AVG_1H(
      "tacos_duration_seconds_avg_1h",
      false,
      "Average response time in seconds of the successful checks in the hour that ends at the"
          + " sample time."),
  DURATION_SECONDS_MAX_1H(
      "tacos_duration_seconds_max_1h",
      false,
      "Maximum response time in seconds of the successful checks in the hour that ends at the"
          + " sample time.");

  private final String metricName;
  private final boolean raw;
  private final String help;

  ProbeMetric(String metricName, boolean raw, String help) {
    this.metricName = metricName;
    this.raw = raw;
    this.help = help;
  }

  public String metricName() {
    return metricName;
  }

  /** True: from the raw check results (kept for some days). False: from the hourly roll-ups. */
  public boolean raw() {
    return raw;
  }

  public String help() {
    return help;
  }
}
