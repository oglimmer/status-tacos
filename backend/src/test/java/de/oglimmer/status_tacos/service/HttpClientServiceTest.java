/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    HttpClientService.HttpCheckResult result = httpClientService.performHealthCheck(testUrl);

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

    HttpClientService.HttpCheckResult result = httpClientService.performHealthCheck(testUrl);

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

    HttpClientService.HttpCheckResult result = httpClientService.performHealthCheck(testUrl);

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

    HttpClientService.HttpCheckResult result = httpClientService.performHealthCheck(testUrl);

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

    HttpClientService.HttpCheckResult result = httpClientService.performHealthCheck(testUrl);

    assertThat(result).isNotNull();
    assertThat(result.getUrl()).isEqualTo(testUrl);
    assertThat(result.getStatusCode()).isEqualTo(200);
    assertThat(result.getIsUp()).isTrue();
    assertThat(result.getResponseTimeMs()).isGreaterThan(0);
    assertThat(result.getErrorMessage()).isNull();
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
}
