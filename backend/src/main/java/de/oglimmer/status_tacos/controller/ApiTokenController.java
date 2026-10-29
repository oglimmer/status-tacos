/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.ApiTokenRequestDto;
import de.oglimmer.status_tacos.dto.ApiTokenResponseDto;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.service.ApiTokenService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** API tokens of the current user, for tools like Grafana (see /prom). */
@Slf4j
@RestController
@RequestMapping("/v1/api-tokens")
@RequiredArgsConstructor
public class ApiTokenController {

  private final ApiTokenService apiTokenService;
  private final UserTenantResolver userTenantResolver;

  @GetMapping
  public List<ApiTokenResponseDto> list() {
    return apiTokenService.list(currentUser());
  }

  @PostMapping
  public ResponseEntity<ApiTokenResponseDto> create(
      @Valid @RequestBody ApiTokenRequestDto request) {
    User user = currentUser();
    ApiTokenResponseDto created = apiTokenService.create(user, request.getName());
    log.info("User {} created API token {}", user.getId(), created.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    User user = currentUser();
    if (!apiTokenService.delete(user, id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such API token");
    }
    log.info("User {} deleted API token {}", user.getId(), id);
    return ResponseEntity.noContent().build();
  }

  /** The user has the maximum number of tokens. */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
  }

  /** API tokens need a user record. GET /v1/users/me creates it. */
  private User currentUser() {
    return userTenantResolver
        .getCurrentUser()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown user"));
  }
}
