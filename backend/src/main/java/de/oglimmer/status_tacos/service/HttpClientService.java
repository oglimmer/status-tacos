/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

/**
 * Runs the HTTP check of a monitor.
 *
 * <p>Each check opens a new connection, so the response time always holds DNS, TCP connect and TLS
 * handshake, and DNS or certificate changes show up at the next check. A check never takes longer
 * than the total timeout and never reads more than the max body size.
 */
@Service
@Slf4j
public class HttpClientService {

  private static final int LOGGED_BODY_CHARS = 500;

  private final CloseableHttpClient httpClient;
  private final PoolingHttpClientConnectionManager connectionManager;
  private final ScheduledExecutorService deadlineScheduler;
  private final Duration totalTimeout;
  private final int maxBodyBytes;
  private final UserRegex userRegex;

  public HttpClientService(
      @Value("${monitor.http.connect-timeout:10s}") Duration connectTimeout,
      @Value("${monitor.http.request-timeout:30s}") Duration requestTimeout,
      @Value("${monitor.http.total-timeout:30s}") Duration totalTimeout,
      @Value("${monitor.http.max-body-size:2MB}") DataSize maxBodySize,
      @Value("${monitor.http.regex-timeout:1s}") Duration regexTimeout,
      @Value("${monitor.http.max-connections:200}") int maxConnections,
      @Value("${monitor.http.max-per-route:20}") int maxPerRoute) {
    this.totalTimeout = totalTimeout;
    this.maxBodyBytes = (int) Math.min(Integer.MAX_VALUE - 1, maxBodySize.toBytes());
    this.userRegex = new UserRegex(regexTimeout);

    this.connectionManager = new PoolingHttpClientConnectionManager();
    this.connectionManager.setMaxTotal(maxConnections);
    this.connectionManager.setDefaultMaxPerRoute(maxPerRoute);
    // The TCP connect timeout must be set here. Without it an unreachable host blocks for the OS
    // default (60-120+ seconds). The socket timeout is the max wait between two reads.
    this.connectionManager.setDefaultConnectionConfig(
        ConnectionConfig.custom()
            .setConnectTimeout(Timeout.of(connectTimeout))
            .setSocketTimeout(Timeout.of(requestTimeout))
            .build());

    this.httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.of(connectTimeout))
                    .setResponseTimeout(Timeout.of(requestTimeout))
                    .build())
            // A monitor measures the full path of a new visitor: no keep-alive between checks.
            .setConnectionReuseStrategy((request, response, context) -> false)
            .build();

    this.deadlineScheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread thread = new Thread(r, "http-check-deadline");
              thread.setDaemon(true);
              return thread;
            });

    log.info(
        "HttpClientService initialized with connect timeout: {}, request timeout: {}, total timeout:"
            + " {}, max body size: {} bytes, max connections: {}, max per route: {}",
        connectTimeout,
        requestTimeout,
        totalTimeout,
        maxBodyBytes,
        maxConnections,
        maxPerRoute);
  }

  @PreDestroy
  public void cleanup() {
    try {
      log.info("Closing HTTP client and releasing connections");
      deadlineScheduler.shutdownNow();
      httpClient.close();
      connectionManager.close();
    } catch (IOException e) {
      log.error("Error closing HTTP client: {}", e.getMessage(), e);
    }
  }

  public HttpCheckResult performHealthCheck(
      String url,
      Map<String, String> customHeaders,
      String statusCodeRegex,
      String responseBodyRegex,
      String prometheusKey,
      Double prometheusMinValue,
      Double prometheusMaxValue) {
    long startTime = System.nanoTime();
    HttpGet request = new HttpGet(url);
    request.setHeader("User-Agent", "StatusTacos-Monitor/1.0");
    request.setHeader("Accept", "*/*");
    if (customHeaders != null) {
      customHeaders.forEach(request::setHeader);
    }

    // The socket timeout only limits the wait between two reads. A server that sends the body
    // very slowly is stopped here.
    AtomicBoolean deadlineHit = new AtomicBoolean();
    ScheduledFuture<?> deadline =
        deadlineScheduler.schedule(
            () -> {
              deadlineHit.set(true);
              request.cancel();
            },
            totalTimeout.toMillis(),
            TimeUnit.MILLISECONDS);

    // executeOpen, not execute with a handler: execute reads the rest of a cut body to the end.
    // Closing the response instead drops the connection.
    try (ClassicHttpResponse response = httpClient.executeOpen(null, request, null)) {
      int responseTime = elapsedMs(startTime);
      int statusCode = response.getCode();
      Body body = readBody(response.getEntity());

      String failure =
          checkSuccessCriteria(
              statusCode,
              body.text(),
              statusCodeRegex,
              responseBodyRegex,
              prometheusKey,
              prometheusMinValue,
              prometheusMaxValue);
      if (failure != null && body.truncated()) {
        failure += " (only the first " + maxBodyBytes + " bytes of the body were read)";
      }
      if (failure != null && log.isDebugEnabled()) {
        log.debug(
            "Check failed for {}: {}, response time {}ms, request headers {}, response headers {},"
                + " body: {}",
            url,
            failure,
            responseTime,
            headerNames(request.getHeaders()),
            headerNames(response.getHeaders()),
            abbreviate(body.text()));
      }

      return HttpCheckResult.builder()
          .url(url)
          .statusCode(statusCode)
          .responseTimeMs(responseTime)
          .isUp(failure == null)
          .responseBody(body.text())
          .errorMessage(failure)
          .build();

    } catch (IOException e) {
      String errorMessage =
          deadlineHit.get()
              ? "Timeout: no complete response within " + totalTimeout.toSeconds() + "s"
              : "Network error: " + e.getMessage();
      // An expected outcome of a check, so no stack trace
      log.debug("Check failed for {}: {} ({})", url, errorMessage, e.getClass().getName());
      return failedResult(url, startTime, errorMessage);

    } catch (Exception e) {
      log.warn("Unexpected error during health check for {}: {}", url, e.getMessage(), e);
      return failedResult(url, startTime, "Unexpected error: " + e.getMessage());

    } finally {
      deadline.cancel(false);
    }
  }

  private record Body(String text, boolean truncated) {}

  /** Reads at most maxBodyBytes. The caller closes the response, so the rest is never read. */
  private Body readBody(HttpEntity entity) throws IOException {
    if (entity == null) {
      return new Body(null, false);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    boolean truncated = false;
    InputStream in = entity.getContent();
    if (in != null) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        int room = maxBodyBytes - out.size();
        if (read > room) {
          out.write(buffer, 0, room);
          truncated = true;
          break;
        }
        out.write(buffer, 0, read);
      }
    }
    return new Body(out.toString(charsetOf(entity)), truncated);
  }

  private static Charset charsetOf(HttpEntity entity) {
    try {
      ContentType contentType = ContentType.parseLenient(entity.getContentType());
      Charset charset = contentType != null ? contentType.getCharset() : null;
      return charset != null ? charset : StandardCharsets.UTF_8;
    } catch (RuntimeException e) {
      return StandardCharsets.UTF_8;
    }
  }

  /**
   * @return null if all criteria are met, else why the check failed
   */
  private String checkSuccessCriteria(
      int statusCode,
      String responseBody,
      String statusCodeRegex,
      String responseBodyRegex,
      String prometheusKey,
      Double prometheusMinValue,
      Double prometheusMaxValue) {
    // One time budget for all regexes of this check
    long deadline = userRegex.deadline();

    if (statusCodeRegex == null || statusCodeRegex.isEmpty()) {
      if (statusCode < 200 || statusCode >= 400) {
        return "HTTP " + statusCode + " response";
      }
    } else {
      try {
        if (!userRegex
            .compile(statusCodeRegex, 0)
            .matcher(UserRegex.withDeadline(String.valueOf(statusCode), deadline))
            .matches()) {
          return "Status code " + statusCode + " does not match pattern: " + statusCodeRegex;
        }
      } catch (PatternSyntaxException e) {
        return "Invalid status code regex: " + statusCodeRegex;
      } catch (UserRegex.TimeoutException e) {
        return regexTimeoutMessage("Status code regex", statusCodeRegex);
      }
    }

    if (responseBodyRegex != null && !responseBodyRegex.isEmpty() && responseBody != null) {
      try {
        if (!userRegex
            .compile(responseBodyRegex, Pattern.DOTALL)
            .matcher(UserRegex.withDeadline(responseBody, deadline))
            .find()) {
          return "Response body does not match pattern: " + responseBodyRegex;
        }
      } catch (PatternSyntaxException e) {
        return "Invalid response body regex: " + responseBodyRegex;
      } catch (UserRegex.TimeoutException e) {
        return regexTimeoutMessage("Response body regex", responseBodyRegex);
      }
    }

    if (prometheusKey != null && !prometheusKey.isEmpty()) {
      Pattern keyPattern;
      try {
        keyPattern = userRegex.compile(prometheusKey, 0);
      } catch (PatternSyntaxException e) {
        return "Invalid Prometheus key regex: " + prometheusKey;
      }
      OptionalDouble sum;
      try {
        sum =
            responseBody != null
                ? PrometheusParser.sumMatching(
                    responseBody,
                    series -> keyPattern.matcher(UserRegex.withDeadline(series, deadline)).find())
                : OptionalDouble.empty();
      } catch (UserRegex.TimeoutException e) {
        return regexTimeoutMessage("Prometheus key regex", prometheusKey);
      }
      if (sum.isEmpty()) {
        return "No Prometheus metric matches key: " + prometheusKey;
      }
      double value = sum.getAsDouble();
      if ((prometheusMinValue != null && value < prometheusMinValue)
          || (prometheusMaxValue != null && value > prometheusMaxValue)) {
        return "Prometheus value "
            + value
            + " for key "
            + prometheusKey
            + " is outside ["
            + (prometheusMinValue != null ? prometheusMinValue : "-")
            + ", "
            + (prometheusMaxValue != null ? prometheusMaxValue : "-")
            + "]";
      }
    }
    return null;
  }

  private String regexTimeoutMessage(String what, String regex) {
    return what + " took longer than " + userRegex.timeout().toMillis() + "ms: " + regex;
  }

  private static HttpCheckResult failedResult(String url, long startTime, String errorMessage) {
    return HttpCheckResult.builder()
        .url(url)
        .statusCode(null)
        .responseTimeMs(elapsedMs(startTime))
        .isUp(false)
        .errorMessage(errorMessage)
        .build();
  }

  private static int elapsedMs(long startNanos) {
    return (int) Math.max(1, (System.nanoTime() - startNanos) / 1_000_000);
  }

  /**
   * Header values can hold secrets (Authorization, API keys, cookies), so only names are logged.
   */
  private static String headerNames(Header[] headers) {
    return headers == null
        ? "[]"
        : Arrays.stream(headers).map(Header::getName).collect(Collectors.joining(", ", "[", "]"));
  }

  private static String abbreviate(String text) {
    if (text == null || text.isEmpty()) {
      return "<empty>";
    }
    return text.length() > LOGGED_BODY_CHARS
        ? text.substring(0, LOGGED_BODY_CHARS) + "... (truncated)"
        : text;
  }

  public static class HttpCheckResult {
    private final String url;
    private final Integer statusCode;
    private final Integer responseTimeMs;
    private final Boolean isUp;
    private final String errorMessage;
    private final String responseBody;

    private HttpCheckResult(Builder builder) {
      this.url = builder.url;
      this.statusCode = builder.statusCode;
      this.responseTimeMs = builder.responseTimeMs;
      this.isUp = builder.isUp;
      this.errorMessage = builder.errorMessage;
      this.responseBody = builder.responseBody;
    }

    public static Builder builder() {
      return new Builder();
    }

    public String getUrl() {
      return url;
    }

    public Integer getStatusCode() {
      return statusCode;
    }

    public Integer getResponseTimeMs() {
      return responseTimeMs;
    }

    public Boolean getIsUp() {
      return isUp;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public String getResponseBody() {
      return responseBody;
    }

    public static class Builder {
      private String url;
      private Integer statusCode;
      private Integer responseTimeMs;
      private Boolean isUp;
      private String errorMessage;
      private String responseBody;

      public Builder url(String url) {
        this.url = url;
        return this;
      }

      public Builder statusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
      }

      public Builder responseTimeMs(Integer responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
        return this;
      }

      public Builder isUp(Boolean isUp) {
        this.isUp = isUp;
        return this;
      }

      public Builder errorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
      }

      public Builder responseBody(String responseBody) {
        this.responseBody = responseBody;
        return this;
      }

      public HttpCheckResult build() {
        return new HttpCheckResult(this);
      }
    }

    @Override
    public String toString() {
      return "HttpCheckResult{"
          + "url='"
          + url
          + '\''
          + ", statusCode="
          + statusCode
          + ", responseTimeMs="
          + responseTimeMs
          + ", isUp="
          + isUp
          + ", errorMessage='"
          + errorMessage
          + '\''
          + '}';
    }
  }
}
