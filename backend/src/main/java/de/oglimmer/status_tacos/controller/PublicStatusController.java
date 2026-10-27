/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.PublicStatusPageDto;
import de.oglimmer.status_tacos.persistence.TenantStatusPageAsset;
import de.oglimmer.status_tacos.service.PublicStatusService;
import de.oglimmer.status_tacos.service.StatusPageAssetService;
import de.oglimmer.status_tacos.service.StatusPageRenderer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** The public status page of a tenant. No sign-in: see {@code SecurityConfig}. */
@RestController
@RequestMapping("/v1/public/{tenantCode}/status")
@RequiredArgsConstructor
public class PublicStatusController {

  private static final CacheControl CACHE = CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic();

  /** A file with the hash of its current content in the URL never changes. */
  private static final CacheControl CACHE_VERSIONED =
      CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();

  private static final CacheControl CACHE_ASSET =
      CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

  /**
   * An uploaded file runs nothing even when opened directly (an SVG can have scripts), and it is on
   * the host of the app.
   */
  private static final String ASSET_POLICY =
      "default-src 'none'; style-src 'unsafe-inline'; sandbox";

  private final PublicStatusService publicStatusService;
  private final StatusPageAssetService assetService;

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

  /**
   * A font or image of the page. The page is in a sandbox (opaque origin), so the browser loads
   * fonts with CORS; the CORS config of {@code SecurityConfig} allows that.
   */
  @GetMapping("/assets/{name}")
  public ResponseEntity<byte[]> getAsset(
      @PathVariable String tenantCode,
      @PathVariable String name,
      @RequestParam(name = "v", required = false) String version,
      WebRequest request) {
    Optional<TenantStatusPageAsset> found = assetService.find(tenantCode, name);
    if (found.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    TenantStatusPageAsset asset = found.get();
    String etag = "\"" + asset.getSha256() + "\"";
    CacheControl cache =
        StatusPageAssetService.version(asset).equals(version) ? CACHE_VERSIONED : CACHE_ASSET;
    if (request.checkNotModified(etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(asset.getContentType()))
        .eTag(etag)
        .cacheControl(cache)
        .header("Content-Security-Policy", ASSET_POLICY)
        .body(asset.getData());
  }

  private static ResponseEntity.BodyBuilder html(HttpStatus status) {
    return ResponseEntity.status(status)
        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
        .header("Content-Security-Policy", StatusPageRenderer.CONTENT_SECURITY_POLICY)
        .header("Referrer-Policy", "no-referrer");
  }
}
