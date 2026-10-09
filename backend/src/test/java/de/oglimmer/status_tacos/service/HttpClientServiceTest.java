/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class HttpClientServiceTest {

  private MockWebServer mockWebServer;
  private HttpClientService httpClientService;
  private String baseUrl;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    baseUrl = mockWebServer.url("/").toString();

    httpClientService =
        new HttpClientService(
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(2), // total timeout
            DataSize.ofBytes(1024), // max body size
            Duration.ofMillis(500), // regex timeout
            200, // max connections
            20 // max per route
            );
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
    httpClientService.cleanup();
  }

  @Test
  void performHealthCheck_withValidUrl_shouldReturnSuccessResult() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    String testUrl = baseUrl + "status/200";

    HttpClientService.HttpCheckResult result = check(testUrl);

    assertThat(result).isNotNull();
    assertThat(result.getUrl()).isEqualTo(testUrl);
    assertThat(result.getStatusCode()).isEqualTo(200);
    assertThat(result.getIsUp()).isTrue();
    assertThat(result.getResponseTimeMs()).isGreaterThan(0);
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  void performHealthCheck_withNotFoundUrl_shouldReturnFailureResult() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));
    String testUrl = baseUrl + "status/404";

    HttpClientService.HttpCheckResult result = check(testUrl);

    assertThat(result).isNotNull();
    assertThat(result.getUrl()).isEqualTo(testUrl);
    assertThat(result.getStatusCode()).isEqualTo(404);
    assertThat(result.getIsUp()).isFalse();
    assertThat(result.getResponseTimeMs()).isGreaterThan(0);
    assertThat(result.getErrorMessage()).contains("HTTP 404");
  }

  @Test
  void performHealthCheck_withServerErrorUrl_shouldReturnFailureResult() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));
    String testUrl = baseUrl + "status/500";

    HttpClientService.HttpCheckResult result = check(testUrl);

    assertThat(result).isNotNull();
    assertThat(result.getUrl()).isEqualTo(testUrl);
    assertThat(result.getStatusCode()).isEqualTo(500);
    assertThat(result.getIsUp()).isFalse();
    assertThat(result.getResponseTimeMs()).isGreaterThan(0);
    assertThat(result.getErrorMessage()).contains("HTTP 500");
  }

  @Test
  void performHealthCheck_withInvalidUrl_shouldReturnNetworkError() {
    String testUrl = "http://nonexistent-host-12345.invalid";

    HttpClientService.HttpCheckResult result = check(testUrl);

    assertThat(result).isNotNull();
    assertThat(result.getUrl()).isEqualTo(testUrl);
    assertThat(result.getStatusCode()).isNull();
    assertThat(result.getIsUp()).isFalse();
    assertThat(result.getResponseTimeMs()).isGreaterThan(0);
    assertThat(result.getErrorMessage()).contains("Network error");
  }

  @Test
  void performHealthCheck_withRedirectUrl_shouldFollowRedirect() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(302).addHeader("Location", baseUrl + "final"));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    String testUrl = baseUrl + "redirect/1";

    HttpClientService.HttpCheckResult result = check(testUrl);

    assertThat(result).isNotNull();
    assertThat(result.getUrl()).isEqualTo(testUrl);
    assertThat(result.getStatusCode()).isEqualTo(200);
    assertThat(result.getIsUp()).isTrue();
    assertThat(result.getResponseTimeMs()).isGreaterThan(0);
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  void bodyLargerThanMaxSize_isCutAndCheckStaysUp() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("a".repeat(5000)));

    HttpClientService.HttpCheckResult result = check(baseUrl);

    assertThat(result.getIsUp()).isTrue();
    assertThat(result.getResponseBody()).hasSize(1024);
  }

  @Test
  void bodyRegexAfterTheMaxSize_failsAndSaysTheBodyWasCut() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody("a".repeat(5000) + "healthy"));

    HttpClientService.HttpCheckResult result =
        httpClientService.performHealthCheck(baseUrl, null, null, "healthy", null, null, null);

    assertThat(result.getIsUp()).isFalse();
    assertThat(result.getErrorMessage())
        .contains("does not match pattern")
        .contains("first 1024 bytes");
  }

  @Test
  void slowBody_isStoppedByTheTotalTimeout() {
    // 100 bytes, 1 byte every 100 ms: 10 s in total, but each read is fast
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody("a".repeat(100))
            .throttleBody(1, 100, TimeUnit.MILLISECONDS));

    long start = System.nanoTime();
    HttpClientService.HttpCheckResult result = check(baseUrl);
    long tookMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(result.getIsUp()).isFalse();
    assertThat(result.getErrorMessage()).startsWith("Timeout");
    assertThat(tookMs).isBetween(1_900L, 5_000L);
  }

  @Test
  void eachCheckOpensANewConnection() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

    check(baseUrl);
    check(baseUrl);

    // The sequence number counts the requests on one connection
    assertThat(mockWebServer.takeRequest().getSequenceNumber()).isZero();
    assertThat(mockWebServer.takeRequest().getSequenceNumber()).isZero();
  }

  @Test
  void regexWithCatastrophicBacktracking_isStoppedByTheRegexTimeout() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("a".repeat(60) + "!"));

    long start = System.nanoTime();
    HttpClientService.HttpCheckResult result =
        httpClientService.performHealthCheck(baseUrl, null, null, "^(.*a){20}$", null, null, null);
    long tookMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(result.getIsUp()).isFalse();
    assertThat(result.getErrorMessage()).contains("took longer than 500ms");
    assertThat(tookMs).isLessThan(3_000L);
  }

  @Test
  void prometheusValueOutsideRange_isDown() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody("# HELP queue\nqueue_size{name=\"a b\"} 7 1700000000000\nqueue_size 5\n"));

    HttpClientService.HttpCheckResult result =
        httpClientService.performHealthCheck(baseUrl, null, null, null, "queue_size", 0.0, 10.0);

    assertThat(result.getIsUp()).isFalse();
    assertThat(result.getErrorMessage()).contains("12.0").contains("outside");
  }

  @Test
  void httpCheckResult_builderPattern_shouldWorkCorrectly() {
    String url = "https://example.com";
    Integer statusCode = 200;
    Integer responseTime = 150;
    Boolean isUp = true;
    String errorMessage = null;

    HttpClientService.HttpCheckResult result =
        HttpClientService.HttpCheckResult.builder()
            .url(url)
            .statusCode(statusCode)
            .responseTimeMs(responseTime)
            .isUp(isUp)
            .errorMessage(errorMessage)
            .build();

    assertThat(result.getUrl()).isEqualTo(url);
    assertThat(result.getStatusCode()).isEqualTo(statusCode);
    assertThat(result.getResponseTimeMs()).isEqualTo(responseTime);
    assertThat(result.getIsUp()).isEqualTo(isUp);
    assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
  }

  @Test
  void httpCheckResult_toString_shouldIncludeAllFields() {
    HttpClientService.HttpCheckResult result =
        HttpClientService.HttpCheckResult.builder()
            .url("https://example.com")
            .statusCode(200)
            .responseTimeMs(150)
            .isUp(true)
            .errorMessage(null)
            .build();

    String toString = result.toString();

    assertThat(toString).contains("https://example.com");
    assertThat(toString).contains("200");
    assertThat(toString).contains("150");
    assertThat(toString).contains("true");
  }

  private HttpClientService.HttpCheckResult check(String url) {
    return httpClientService.performHealthCheck(url, null, null, null, null, null, null);
  }
}
