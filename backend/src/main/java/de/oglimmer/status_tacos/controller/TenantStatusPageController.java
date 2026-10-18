/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.StatusPageConfigDto;
import de.oglimmer.status_tacos.dto.StatusPagePreviewDto;
import de.oglimmer.status_tacos.dto.StatusPageRequestDto;
import de.oglimmer.status_tacos.service.StatusPageService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The editor of the look of the public status page of a tenant. Members of the tenant only. */
@RestController
@RequestMapping("/v1/tenants/{id}/status-page")
@RequiredArgsConstructor
@Slf4j
public class TenantStatusPageController {

  private final StatusPageService statusPageService;
  private final UserTenantResolver userTenantResolver;

  @GetMapping
  public StatusPageConfigDto getConfig(@PathVariable Integer id) {
    checkAccess(id);
    return statusPageService.getConfig(id);
  }

  @PutMapping
  public StatusPageConfigDto save(
      @PathVariable Integer id, @RequestBody StatusPageRequestDto request) {
    checkAccess(id);
    log.info("Saving the status page look of tenant {}", id);
    return statusPageService.save(id, request);
  }

  @DeleteMapping
  public StatusPageConfigDto reset(@PathVariable Integer id) {
    checkAccess(id);
    log.info("Resetting the status page look of tenant {}", id);
    return statusPageService.reset(id);
  }

  @PostMapping("/preview")
  public StatusPagePreviewDto preview(
      @PathVariable Integer id, @RequestBody StatusPageRequestDto request) {
    checkAccess(id);
    return statusPageService.preview(id, request);
  }

  /** An invalid template or CSS. The frontend shows the message. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "invalid_status_page", "message", e.getMessage()));
  }

  private void checkAccess(Integer tenantId) {
    if (!userTenantResolver.hasAccessToTenant(tenantId)) {
      log.warn("User attempted to access the status page of unauthorized tenant: {}", tenantId);
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }
}
