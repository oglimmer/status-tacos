/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HttpClientService {

  private final CloseableHttpClient httpClient;
  private final PoolingHttpClientConnectionManager connectionManager;
  private final Timeout connectTimeout;
  private final Timeout requestTimeout;

  public HttpClientService(
      @Value("${monitor.http.connect-timeout:10s}") java.time.Duration connectTimeoutDuration,
      @Value("${monitor.http.request-timeout:30s}") java.time.Duration requestTimeoutDuration,
      @Value("${monitor.http.max-connections:200}") int maxConnections,
      @Value("${monitor.http.max-per-route:20}") int maxPerRoute) {

    this.connectTimeout = Timeout.ofMilliseconds(connectTimeoutDuration.toMillis());
    this.requestTimeout = Timeout.ofMilliseconds(requestTimeoutDuration.toMillis());

    // Configure connection pool with limits to prevent memory leaks
    this.connectionManager = new PoolingHttpClientConnectionManager();
    this.connectionManager.setMaxTotal(maxConnections);
    this.connectionManager.setDefaultMaxPerRoute(maxPerRoute);

    // Set TCP connect timeout on the connection manager - without this,
    // unreachable hosts block for the OS default timeout (60-120+ seconds)
    ConnectionConfig connectionConfig =
        ConnectionConfig.custom()
            .setConnectTimeout(this.connectTimeout)
            .setSocketTimeout(this.requestTimeout)
            .build();
    this.connectionManager.setDefaultConnectionConfig(connectionConfig);

    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(this.connectTimeout)
            .setResponseTimeout(this.requestTimeout)
            .build();

    this.httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(Timeout.ofSeconds(30))
            .evictExpiredConnections()
            .build();

    log.info(
        "HttpClientService initialized with connect timeout: {}ms, request timeout: {}ms, max connections: {}, max per route: {}",
        connectTimeout.toMilliseconds(),
        requestTimeout.toMilliseconds(),
        maxConnections,
        maxPerRoute);
  }

  @PreDestroy
  public void cleanup() {
    try {
      log.info("Closing HTTP client and releasing connections");
      httpClient.close();
      connectionManager.close();
    } catch (IOException e) {
      log.error("Error closing HTTP client: {}", e.getMessage(), e);
    }
  }

  public HttpCheckResult performHealthCheck(String url) {
    log.debug("Performing health check for URL: {}", url);

    long startTime = System.nanoTime();
    HttpGet request = new HttpGet(url);
    request.setHeader("User-Agent", "StatusTacos-Monitor/1.0");
    request.setHeader("Accept", "*/*");

    try {
      return httpClient.execute(
          request,
          response -> {
            long responseTime = Math.max(1, (System.nanoTime() - startTime) / 1_000_000);
            int statusCode = response.getCode();
            boolean isUp = statusCode >= 200 && statusCode < 400;

            // Read response body before consuming for error logging
            String responseBody = null;
            if (response.getEntity() != null) {
              responseBody = EntityUtils.toString(response.getEntity());
            }

            // Log detailed information for failed requests (status >= 400)
            if (statusCode >= 400) {
              StringBuilder errorDetails = new StringBuilder();
              errorDetails.append("\n=== Connection Test Failed ===\n");
              errorDetails.append("URL: ").append(url).append("\n");
              errorDetails.append("Status Code: ").append(statusCode).append("\n");
              errorDetails.append("Response Time: ").append(responseTime).append("ms\n");

              // Log response headers
              errorDetails.append("\n--- Response Headers ---\n");
              if (response.getHeaders() != null && response.getHeaders().length > 0) {
                for (var header : response.getHeaders()) {
                  errorDetails
                      .append(header.getName())
                      .append(": ")
                      .append(header.getValue())
                      .append("\n");
                }
              } else {
                errorDetails.append("No headers\n");
              }

              // Log request headers
              errorDetails.append("\n--- Request Headers ---\n");
              if (request.getHeaders() != null && request.getHeaders().length > 0) {
                for (var header : request.getHeaders()) {
                  errorDetails
                      .append(header.getName())
                      .append(": ")
                      .append(header.getValue())
                      .append("\n");
                }
              }

              // Log response body (truncate if too large)
              errorDetails.append("\n--- Response Body ---\n");
              if (responseBody != null && !responseBody.isEmpty()) {
                if (responseBody.length() > 1000) {
                  errorDetails.append(responseBody.substring(0, 1000)).append("... (truncated)\n");
                } else {
                  errorDetails.append(responseBody).append("\n");
                }
              } else {
                errorDetails.append("Empty response body\n");
              }
              errorDetails.append("==============================");

              log.error("Connection test failed with status {}: {}", statusCode, errorDetails);
            }

            log.debug(
                "Health check completed for {}: status={}, responseTime={}ms, isUp={}",
                url,
                statusCode,
                responseTime,
                isUp);

            return HttpCheckResult.builder()
                .url(url)
                .statusCode(statusCode)
                .responseTimeMs((int) responseTime)
                .isUp(isUp)
                .errorMessage(isUp ? null : "HTTP " + statusCode + " response")
                .build();
          });

    } catch (IOException e) {
      long responseTime = Math.max(1, (System.nanoTime() - startTime) / 1_000_000);
      String errorMessage = "Network error: " + e.getMessage();

      // Log detailed exception information
      StringBuilder errorDetails = new StringBuilder();
      errorDetails.append("\n=== Connection Test Failed (IOException) ===\n");
      errorDetails.append("URL: ").append(url).append("\n");
      errorDetails.append("Error: ").append(e.getClass().getName()).append("\n");
      errorDetails.append("Message: ").append(e.getMessage()).append("\n");
      errorDetails.append("Response Time: ").append(responseTime).append("ms\n");

      // Log request headers
      errorDetails.append("\n--- Request Headers ---\n");
      if (request.getHeaders() != null && request.getHeaders().length > 0) {
        for (var header : request.getHeaders()) {
          errorDetails.append(header.getName()).append(": ").append(header.getValue()).append("\n");
        }
      }

      errorDetails.append("\n--- Stack Trace ---\n");
      for (StackTraceElement element : e.getStackTrace()) {
        errorDetails.append(element.toString()).append("\n");
        if (errorDetails.length() > 2000) {
          errorDetails.append("... (truncated)\n");
          break;
        }
      }
      errorDetails.append("==========================================");

      log.error("Health check failed for {} due to IOException: {}", url, errorDetails);

      return HttpCheckResult.builder()
          .url(url)
          .statusCode(null)
          .responseTimeMs((int) responseTime)
          .isUp(false)
          .errorMessage(errorMessage)
          .build();

    } catch (Exception e) {
      long responseTime = Math.max(1, (System.nanoTime() - startTime) / 1_000_000);
      String errorMessage = "Unexpected error: " + e.getMessage();

      // Log detailed exception information
      StringBuilder errorDetails = new StringBuilder();
      errorDetails.append("\n=== Connection Test Failed (Unexpected Error) ===\n");
      errorDetails.append("URL: ").append(url).append("\n");
      errorDetails.append("Error: ").append(e.getClass().getName()).append("\n");
      errorDetails.append("Message: ").append(e.getMessage()).append("\n");
      errorDetails.append("Response Time: ").append(responseTime).append("ms\n");

      // Log request headers
      errorDetails.append("\n--- Request Headers ---\n");
      if (request.getHeaders() != null && request.getHeaders().length > 0) {
        for (var header : request.getHeaders()) {
          errorDetails.append(header.getName()).append(": ").append(header.getValue()).append("\n");
        }
      }

      errorDetails.append("\n--- Stack Trace ---\n");
      for (StackTraceElement element : e.getStackTrace()) {
        errorDetails.append(element.toString()).append("\n");
        if (errorDetails.length() > 2000) {
          errorDetails.append("... (truncated)\n");
          break;
        }
      }
      errorDetails.append("================================================");

      log.error("Unexpected error during health check for {}: {}", url, errorDetails, e);

      return HttpCheckResult.builder()
          .url(url)
          .statusCode(null)
          .responseTimeMs((int) responseTime)
          .isUp(false)
          .errorMessage(errorMessage)
          .build();
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
    log.debug("Performing health check for URL: {} with custom criteria", url);

    long startTime = System.nanoTime();
    HttpGet request = new HttpGet(url);
    request.setHeader("User-Agent", "StatusTacos-Monitor/1.0");
    request.setHeader("Accept", "*/*");

    if (customHeaders != null) {
      for (Map.Entry<String, String> header : customHeaders.entrySet()) {
        request.setHeader(header.getKey(), header.getValue());
      }
    }

    try {
      return httpClient.execute(
          request,
          response -> {
            long responseTime = Math.max(1, (System.nanoTime() - startTime) / 1_000_000);
            int statusCode = response.getCode();

            // Read response body and ensure connection is released
            String responseBody = null;
            if (response.getEntity() != null) {
              responseBody = EntityUtils.toString(response.getEntity());
            }

            boolean isUp =
                evaluateSuccessCriteria(
                    statusCode,
                    responseBody,
                    statusCodeRegex,
                    responseBodyRegex,
                    prometheusKey,
                    prometheusMinValue,
                    prometheusMaxValue);

            // Log detailed information for failed requests (status >= 400 or custom criteria
            // failed)
            if (statusCode >= 400 || !isUp) {
              StringBuilder errorDetails = new StringBuilder();
              errorDetails.append("\n=== Connection Test Failed (Advanced Check) ===\n");
              errorDetails.append("URL: ").append(url).append("\n");
              errorDetails.append("Status Code: ").append(statusCode).append("\n");
              errorDetails.append("Response Time: ").append(responseTime).append("ms\n");
              errorDetails.append("Criteria Met: ").append(isUp ? "YES" : "NO").append("\n");

              // Log custom criteria if present
              if (statusCodeRegex != null && !statusCodeRegex.isEmpty()) {
                errorDetails.append("Status Code Regex: ").append(statusCodeRegex).append("\n");
              }
              if (responseBodyRegex != null && !responseBodyRegex.isEmpty()) {
                errorDetails.append("Response Body Regex: ").append(responseBodyRegex).append("\n");
              }
              if (prometheusKey != null && !prometheusKey.isEmpty()) {
                errorDetails
                    .append("Prometheus Key: ")
                    .append(prometheusKey)
                    .append(", Min: ")
                    .append(prometheusMinValue)
                    .append(", Max: ")
                    .append(prometheusMaxValue)
                    .append("\n");
              }

              // Log response headers
              errorDetails.append("\n--- Response Headers ---\n");
              if (response.getHeaders() != null && response.getHeaders().length > 0) {
                for (var header : response.getHeaders()) {
                  errorDetails
                      .append(header.getName())
                      .append(": ")
                      .append(header.getValue())
                      .append("\n");
                }
              } else {
                errorDetails.append("No headers\n");
              }

              // Log request headers
              errorDetails.append("\n--- Request Headers ---\n");
              if (request.getHeaders() != null && request.getHeaders().length > 0) {
                for (var header : request.getHeaders()) {
                  errorDetails
                      .append(header.getName())
                      .append(": ")
                      .append(header.getValue())
                      .append("\n");
                }
              }

              // Log response body (truncate if too large)
              errorDetails.append("\n--- Response Body ---\n");
              if (responseBody != null && !responseBody.isEmpty()) {
                if (responseBody.length() > 1000) {
                  errorDetails.append(responseBody.substring(0, 1000)).append("... (truncated)\n");
                } else {
                  errorDetails.append(responseBody).append("\n");
                }
              } else {
                errorDetails.append("Empty response body\n");
              }
              errorDetails.append("===============================================");

              log.error("Connection test failed with status {}: {}", statusCode, errorDetails);
            }

            log.debug(
                "Health check completed for {}: status={}, responseTime={}ms, isUp={}",
                url,
                statusCode,
                responseTime,
                isUp);

            return HttpCheckResult.builder()
                .url(url)
                .statusCode(statusCode)
                .responseTimeMs((int) responseTime)
                .isUp(isUp)
                .responseBody(responseBody)
                .errorMessage(
                    isUp
                        ? null
                        : buildErrorMessage(
                            statusCode, responseBody, statusCodeRegex, responseBodyRegex))
                .build();
          });

    } catch (IOException e) {
      long responseTime = Math.max(1, (System.nanoTime() - startTime) / 1_000_000);
      String errorMessage = "Network error: " + e.getMessage();

      // Log detailed exception information
      StringBuilder errorDetails = new StringBuilder();
      errorDetails.append("\n=== Connection Test Failed (IOException - Advanced Check) ===\n");
      errorDetails.append("URL: ").append(url).append("\n");
      errorDetails.append("Error: ").append(e.getClass().getName()).append("\n");
      errorDetails.append("Message: ").append(e.getMessage()).append("\n");
      errorDetails.append("Response Time: ").append(responseTime).append("ms\n");

      // Log custom criteria if present
      if (statusCodeRegex != null && !statusCodeRegex.isEmpty()) {
        errorDetails.append("Status Code Regex: ").append(statusCodeRegex).append("\n");
      }
      if (responseBodyRegex != null && !responseBodyRegex.isEmpty()) {
        errorDetails.append("Response Body Regex: ").append(responseBodyRegex).append("\n");
      }

      // Log request headers
      errorDetails.append("\n--- Request Headers ---\n");
      if (request.getHeaders() != null && request.getHeaders().length > 0) {
        for (var header : request.getHeaders()) {
          errorDetails.append(header.getName()).append(": ").append(header.getValue()).append("\n");
        }
      }

      errorDetails.append("\n--- Stack Trace ---\n");
      for (StackTraceElement element : e.getStackTrace()) {
        errorDetails.append(element.toString()).append("\n");
        if (errorDetails.length() > 2000) {
          errorDetails.append("... (truncated)\n");
          break;
        }
      }
      errorDetails.append("============================================================");

      log.error("Health check failed for {} due to IOException: {}", url, errorDetails);

      return HttpCheckResult.builder()
          .url(url)
          .statusCode(null)
          .responseTimeMs((int) responseTime)
          .isUp(false)
          .errorMessage(errorMessage)
          .build();

    } catch (Exception e) {
      long responseTime = Math.max(1, (System.nanoTime() - startTime) / 1_000_000);
      String errorMessage = "Unexpected error: " + e.getMessage();

      // Log detailed exception information
      StringBuilder errorDetails = new StringBuilder();
      errorDetails.append("\n=== Connection Test Failed (Unexpected Error - Advanced Check) ===\n");
      errorDetails.append("URL: ").append(url).append("\n");
      errorDetails.append("Error: ").append(e.getClass().getName()).append("\n");
      errorDetails.append("Message: ").append(e.getMessage()).append("\n");
      errorDetails.append("Response Time: ").append(responseTime).append("ms\n");

      // Log custom criteria if present
      if (statusCodeRegex != null && !statusCodeRegex.isEmpty()) {
        errorDetails.append("Status Code Regex: ").append(statusCodeRegex).append("\n");
      }
      if (responseBodyRegex != null && !responseBodyRegex.isEmpty()) {
        errorDetails.append("Response Body Regex: ").append(responseBodyRegex).append("\n");
      }

      // Log request headers
      errorDetails.append("\n--- Request Headers ---\n");
      if (request.getHeaders() != null && request.getHeaders().length > 0) {
        for (var header : request.getHeaders()) {
          errorDetails.append(header.getName()).append(": ").append(header.getValue()).append("\n");
        }
      }

      errorDetails.append("\n--- Stack Trace ---\n");
      for (StackTraceElement element : e.getStackTrace()) {
        errorDetails.append(element.toString()).append("\n");
        if (errorDetails.length() > 2000) {
          errorDetails.append("... (truncated)\n");
          break;
        }
      }
      errorDetails.append("==================================================================");

      log.error("Unexpected error during health check for {}: {}", url, errorDetails, e);

      return HttpCheckResult.builder()
          .url(url)
          .statusCode(null)
          .responseTimeMs((int) responseTime)
          .isUp(false)
          .errorMessage(errorMessage)
          .build();
    }
  }

  private boolean evaluateSuccessCriteria(
      int statusCode,
      String responseBody,
      String statusCodeRegex,
      String responseBodyRegex,
      String prometheusKey,
      Double prometheusMinValue,
      Double prometheusMaxValue) {
    try {
      if (statusCodeRegex != null && !statusCodeRegex.isEmpty()) {
        Pattern statusPattern = Pattern.compile(statusCodeRegex);
        if (!statusPattern.matcher(String.valueOf(statusCode)).matches()) {
          log.debug("Status code {} does not match pattern {}", statusCode, statusCodeRegex);
          return false;
        }
      } else {
        if (statusCode < 200 || statusCode >= 400) {
          return false;
        }
      }

      if (responseBodyRegex != null && !responseBodyRegex.isEmpty() && responseBody != null) {
        Pattern bodyPattern = Pattern.compile(responseBodyRegex, Pattern.DOTALL);
        if (!bodyPattern.matcher(responseBody).find()) {
          log.debug("Response body does not match pattern {}", responseBodyRegex);
          return false;
        }
      }

      if (prometheusKey != null && !prometheusKey.isEmpty()) {
        if (!evaluatePrometheusResult(
            responseBody, prometheusKey, prometheusMinValue, prometheusMaxValue)) {
          log.debug(
              "Prometheus result does not meet criteria for key: {}, min: {}, max: {}",
              prometheusKey,
              prometheusMinValue,
              prometheusMaxValue);
          return false;
        }
      }

      return true;
    } catch (PatternSyntaxException e) {
      log.error("Invalid regex pattern: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Evaluates a Prometheus result by summing matching values and checking against range
   *
   * @param prometheusResult The Prometheus query result as a string
   * @param keyRegex The regex pattern to match metric names/keys
   * @param minValue The minimum allowed value (null if no minimum)
   * @param maxValue The maximum allowed value (null if no maximum)
   * @return true if the summed value is within the specified range, false otherwise
   */
  private static boolean evaluatePrometheusResult(
      String prometheusResult, String keyRegex, Double minValue, Double maxValue) {
    if (prometheusResult == null || keyRegex == null) {
      return false;
    }

    double totalValue = 0.0;
    boolean foundMatch = false;

    Pattern keyPattern = Pattern.compile(keyRegex);

    String[] lines = prometheusResult.split("\\n");

    for (String line : lines) {
      line = line.trim();

      // Skip comments and empty lines
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      String[] lineMatcher = line.split("\\s+", 2);
      String metricName = lineMatcher[0];
      String valueStr = lineMatcher[1];

      Matcher keyMatcher = keyPattern.matcher(metricName);
      if (keyMatcher.find()) {
        try {
          double value = Double.parseDouble(valueStr);
          totalValue += value;
          foundMatch = true;
        } catch (NumberFormatException e) {
          // Skip invalid numbers
        }
      }
    }

    // If no matching keys were found, return false
    if (!foundMatch) {
      return false;
    }

    // Check against min/max constraints
    if (minValue != null && totalValue < minValue) {
      return false;
    }

    if (maxValue != null && totalValue > maxValue) {
      return false;
    }

    return true;
  }

  private String buildErrorMessage(
      int statusCode, String responseBody, String statusCodeRegex, String responseBodyRegex) {
    if (statusCodeRegex != null && !statusCodeRegex.isEmpty()) {
      try {
        Pattern statusPattern = Pattern.compile(statusCodeRegex);
        if (!statusPattern.matcher(String.valueOf(statusCode)).matches()) {
          return "Status code " + statusCode + " does not match pattern: " + statusCodeRegex;
        }
      } catch (PatternSyntaxException e) {
        return "Invalid status code regex: " + statusCodeRegex;
      }
    }

    if (responseBodyRegex != null && !responseBodyRegex.isEmpty() && responseBody != null) {
      try {
        Pattern bodyPattern = Pattern.compile(responseBodyRegex, Pattern.DOTALL);
        if (!bodyPattern.matcher(responseBody).find()) {
          return "Response body does not match pattern: " + responseBodyRegex;
        }
      } catch (PatternSyntaxException e) {
        return "Invalid response body regex: " + responseBodyRegex;
      }
    }

    return "HTTP " + statusCode + " response";
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
