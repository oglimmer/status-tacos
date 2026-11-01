/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The error pages through a real server: MockMvc does not forward to {@code /error}, Tomcat does.
 * Browsers get the branded page, API clients get what they got before.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ErrorPageTest {

  private static final String BROWSER = "text/html,application/xhtml+xml,*/*;q=0.8";

  @LocalServerPort private int port;

  private final HttpClient client = HttpClient.newHttpClient();

  private HttpResponse<String> send(String method, String path, String accept) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
            .header("Accept", accept)
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static void assertBranded(HttpResponse<String> response, int status, String title) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
    assertThat(response.body())
        .contains("Status Tacos")
        .contains("<p class=\"code\">" + status + "</p>")
        .contains(title)
        .doesNotContain("Whitelabel");
  }

  @Test
  void unknownPath_browser_getsBranded404() throws Exception {
    assertBranded(send("GET", "/v1/public/acme/status/nope", BROWSER), 404, "No taco here");
  }

  @Test
  void unknownPath_apiClient_getsJson() throws Exception {
    HttpResponse<String> response = send("GET", "/v1/public/acme/status/nope", "application/json");
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).contains("\"status\":404").doesNotContain("<html");
  }

  @Test
  void unknownStatusPage_browser_getsBranded404() throws Exception {
    assertBranded(send("GET", "/v1/public/no-such-tenant/status/page", BROWSER), 404, "No taco");
  }

  @Test
  void writeOnPublicPath_browser_getsBranded403() throws Exception {
    assertBranded(send("POST", "/v1/public/acme/status", BROWSER), 403, "Not for you");
  }

  @Test
  void noSignIn_browser_getsBranded401() throws Exception {
    HttpResponse<String> response = send("GET", "/v1/monitors", BROWSER);
    assertBranded(response, 401, "Sign in first");
    assertThat(response.headers().firstValue("WWW-Authenticate")).isPresent();
  }

  @Test
  void noSignIn_apiClient_getsEmpty401() throws Exception {
    HttpResponse<String> response = send("GET", "/v1/monitors", "application/json");
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).isEmpty();
  }

  @Test
  void render_knowsUnusualCodes() {
    assertThat(ErrorPage.render(502)).contains("The kitchen is on fire");
    assertThat(ErrorPage.render(418)).contains("I&#39;m a teapot");
    assertThat(ErrorPage.render(599)).contains("Something went wrong");
  }
}
