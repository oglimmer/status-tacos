/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.promql.PromQlEngine;
import de.oglimmer.status_tacos.promql.PromQlEngine.InstantVector;
import de.oglimmer.status_tacos.promql.PromQlEngine.RangeSeries;
import de.oglimmer.status_tacos.promql.PromQlEngine.Scalar;
import de.oglimmer.status_tacos.promql.PromQlException;
import de.oglimmer.status_tacos.promql.PromQlParser;
import de.oglimmer.status_tacos.service.PromQueryService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A subset of the Prometheus HTTP API (https://prometheus.io/docs/prometheus/latest/querying/api/),
 * so Grafana can use Status Tacos as a Prometheus data source with the URL {@code
 * https://<host>/api/prom}. Signed in with an API token (see SecurityConfig). Grafana sends GET or
 * POST form requests: each endpoint takes both.
 */
@RestController
@RequestMapping("/prom/api/v1")
@RequiredArgsConstructor
public class PromApiController {

  /** Grafana reads the version to decide which API features it uses. */
  static final String PROMETHEUS_VERSION = "2.53.0";

  private final PromQueryService promQueryService;
  private final UserTenantResolver userTenantResolver;
  private final Clock clock;

  @RequestMapping(
      path = "/query",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> query(
      @RequestParam String query, @RequestParam(required = false) String time) {
    long timeMillis = parseTime(time, clock.millis());
    PromQlEngine.Value value =
        promQueryService.instantQuery(
            userTenantResolver.getCurrentUserTenantIds(), query, timeMillis);
    Map<String, Object> data = new LinkedHashMap<>();
    if (value instanceof Scalar scalar) {
      data.put("resultType", "scalar");
      data.put("result", point(timeMillis, scalar.value()));
    } else {
      data.put("resultType", "vector");
      data.put(
          "result",
          ((InstantVector) value)
              .samples().stream()
                  .map(
                      sample ->
                          Map.of(
                              "metric", sample.labels(),
                              "value", point(timeMillis, sample.value())))
                  .toList());
    }
    return success(data);
  }

  @RequestMapping(
      path = "/query_range",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> queryRange(
      @RequestParam String query,
      @RequestParam String start,
      @RequestParam String end,
      @RequestParam String step) {
    List<RangeSeries> series =
        promQueryService.rangeQuery(
            userTenantResolver.getCurrentUserTenantIds(),
            query,
            parseTime(start, 0),
            parseTime(end, 0),
            PromQlParser.parseDurationMillis(step));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("resultType", "matrix");
    data.put(
        "result",
        series.stream()
            .map(
                s ->
                    Map.of(
                        "metric",
                        s.labels(),
                        "values",
                        s.points().stream().map(p -> point(p.timeMillis(), p.value())).toList()))
            .toList());
    return success(data);
  }

  @RequestMapping(
      path = "/series",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> series(
      @RequestParam(name = "match[]", required = false) List<String> match) {
    return success(promQueryService.series(userTenantResolver.getCurrentUserTenantIds(), match));
  }

  @RequestMapping(
      path = "/labels",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> labels(
      @RequestParam(name = "match[]", required = false) List<String> match) {
    return success(
        promQueryService.labelNames(userTenantResolver.getCurrentUserTenantIds(), match));
  }

  @RequestMapping(
      path = "/label/{name}/values",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> labelValues(
      @PathVariable String name,
      @RequestParam(name = "match[]", required = false) List<String> match) {
    return success(
        promQueryService.labelValues(userTenantResolver.getCurrentUserTenantIds(), name, match));
  }

  @RequestMapping(
      path = "/metadata",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> metadata() {
    return success(promQueryService.metadata());
  }

  @RequestMapping(
      path = "/status/buildinfo",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> buildInfo() {
    return success(
        Map.of(
            "version",
            PROMETHEUS_VERSION,
            "revision",
            "status-tacos",
            "branch",
            "",
            "buildUser",
            "",
            "buildDate",
            "",
            "goVersion",
            ""));
  }

  /** No exemplars, alerts or rules: Grafana asks for them, an empty answer is fine. */
  @RequestMapping(
      path = "/query_exemplars",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> exemplars() {
    return success(List.of());
  }

  @RequestMapping(
      path = "/rules",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> rules() {
    return success(Map.of("groups", List.of()));
  }

  @RequestMapping(
      path = "/alerts",
      method = {RequestMethod.GET, RequestMethod.POST})
  public Map<String, Object> alerts() {
    return success(Map.of("alerts", List.of()));
  }

  @ExceptionHandler(PromQlException.class)
  public ResponseEntity<Map<String, Object>> queryError(PromQlException e) {
    HttpStatus status =
        "bad_data".equals(e.getErrorType())
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.UNPROCESSABLE_ENTITY;
    return error(status, e.getErrorType(), e.getMessage());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> missingParameter(
      MissingServletRequestParameterException e) {
    return error(
        HttpStatus.BAD_REQUEST, "bad_data", "missing parameter \"" + e.getParameterName() + "\"");
  }

  /** Unix seconds with decimals ("1761696000.5") or RFC 3339. */
  static long parseTime(String text, long defaultMillis) {
    if (text == null || text.isBlank()) {
      return defaultMillis;
    }
    try {
      return new BigDecimal(text.trim()).movePointRight(3).longValue();
    } catch (NumberFormatException e) {
      // not unix seconds: RFC 3339
    }
    try {
      return OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli();
    } catch (DateTimeParseException e) {
      throw new PromQlException("cannot parse \"" + text + "\" to a valid timestamp");
    }
  }

  /** [unix seconds, "value"], as Prometheus writes a sample. */
  private static List<Object> point(long timeMillis, double value) {
    return List.of(BigDecimal.valueOf(timeMillis, 3), formatValue(value));
  }

  static String formatValue(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (Double.isInfinite(value)) {
      return value > 0 ? "+Inf" : "-Inf";
    }
    if (value == 0) {
      return "0";
    }
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  private static Map<String, Object> success(Object data) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "success");
    body.put("data", data);
    return body;
  }

  private static ResponseEntity<Map<String, Object>> error(
      HttpStatus status, String errorType, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "error");
    body.put("errorType", errorType);
    body.put("error", message);
    return ResponseEntity.status(status).body(body);
  }
}
