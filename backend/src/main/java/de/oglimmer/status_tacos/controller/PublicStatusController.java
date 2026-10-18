/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.PublicStatusPageDto;
import de.oglimmer.status_tacos.service.PublicStatusService;
import de.oglimmer.status_tacos.service.StatusPageRenderer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public status page of a tenant. No sign-in: see {@code SecurityConfig}. */
@RestController
@RequestMapping("/v1/public/{tenantCode}/status")
@RequiredArgsConstructor
public class PublicStatusController {

  private static final CacheControl CACHE = CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic();

  private final PublicStatusService publicStatusService;

  /** The data of the page, for own pages and tools. */
  @GetMapping
  public ResponseEntity<PublicStatusPageDto> getPublicStatusPage(@PathVariable String tenantCode) {
    return publicStatusService
        .getPublicStatusPage(tenantCode)
        .map(page -> ResponseEntity.ok().cacheControl(CACHE).body(page))
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * The branded page as HTML. The CSP header blocks all scripts and puts the page in a sandbox, so
   * the HTML of the tenant cannot reach the app, even on the same host.
   */
  @GetMapping(value = "/page", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getPublicStatusHtml(@PathVariable String tenantCode) {
    return publicStatusService
        .getPublicStatusHtml(tenantCode)
        .map(html -> html(HttpStatus.OK).cacheControl(CACHE).body(html))
        .orElse(
            html(HttpStatus.NOT_FOUND)
                .body(
                    "<!doctype html><title>Not found</title><p>There is no status page here.</p>"));
  }

  private static ResponseEntity.BodyBuilder html(HttpStatus status) {
    return ResponseEntity.status(status)
        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
        .header("Content-Security-Policy", StatusPageRenderer.CONTENT_SECURITY_POLICY)
        .header("Referrer-Policy", "no-referrer");
  }
}
