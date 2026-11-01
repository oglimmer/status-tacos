/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.util.HtmlUtils;

/**
 * The branded HTML error page for browsers. API clients get JSON as before: only a request that
 * asks for {@code text/html} gets this page (see {@link ErrorPageConfig}).
 */
public final class ErrorPage {

  public static final MediaType CONTENT_TYPE =
      new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

  private static final String TEMPLATE = load();

  private ErrorPage() {}

  /** The page for this status code, for example 404. */
  public static String render(int statusCode) {
    HttpStatus status = HttpStatus.resolve(statusCode);
    String[] text = texts(status);
    return TEMPLATE
        .replace("{{status}}", String.valueOf(statusCode))
        .replace("{{title}}", HtmlUtils.htmlEscape(text[0]))
        .replace("{{message}}", HtmlUtils.htmlEscape(text[1]));
  }

  /** True when the client is a browser that wants a page, not an API client that wants JSON. */
  public static boolean wantsHtml(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
  }

  private static String[] texts(HttpStatus status) {
    if (status == null) {
      return new String[] {"Something went wrong", "The server could not answer this request."};
    }
    return switch (status) {
      case BAD_REQUEST ->
          new String[] {"That order is wrong", "The server could not read this request."};
      case UNAUTHORIZED ->
          new String[] {"Sign in first", "You must sign in to see this. Go to the start page."};
      case FORBIDDEN -> new String[] {"Not for you", "You are not allowed to see this."};
      case NOT_FOUND ->
          new String[] {
            "No taco here", "We looked everywhere. This page does not exist, or it moved."
          };
      case METHOD_NOT_ALLOWED ->
          new String[] {"Wrong way in", "This address does not accept this kind of request."};
      case TOO_MANY_REQUESTS ->
          new String[] {"Slow down", "Too many requests. Please wait a moment and try again."};
      case SERVICE_UNAVAILABLE ->
          new String[] {"Back in a minute", "Status Tacos is not ready. Please try again soon."};
      default ->
          status.is5xxServerError()
              ? new String[] {
                "The kitchen is on fire",
                "Something went wrong on our side. Please try again in a minute."
              }
              : new String[] {
                status.getReasonPhrase(), "The server could not answer this request."
              };
    };
  }

  private static String load() {
    try (InputStream in = ErrorPage.class.getResourceAsStream("/error-page/error.html")) {
      if (in == null) {
        throw new IllegalStateException("error-page/error.html is missing");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
