/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.AccountDeletionResponseDto;
import de.oglimmer.status_tacos.dto.UserResponseDto;
import de.oglimmer.status_tacos.mapper.EntityMapper;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.service.AccountDeletionService;
import de.oglimmer.status_tacos.service.TenantManagementService;
import de.oglimmer.status_tacos.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  private final UserService userService;
  private final TenantManagementService tenantManagementService;
  private final EntityMapper entityMapper;
  private final AccountDeletionService accountDeletionService;

  @GetMapping("/me")
  public ResponseEntity<UserResponseDto> getCurrentUser() {
    try {
      User user = userService.getCurrentUser();

      // If user doesn't exist, create them with a tenant
      if (user == null) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
          String subject = jwt.getSubject();
          if (subject != null && !subject.isEmpty()) {
            log.info("Creating new user for subject: {}", subject);
            user = tenantManagementService.createUserWithTenant(jwt, subject);
          }
        }
      }

      if (user == null) {
        log.error("Unable to get or create current user");
        return ResponseEntity.status(401).build();
      }

      UserResponseDto response = convertToDto(user);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error getting current user: {}", e.getMessage());
      return ResponseEntity.status(401).build();
    }
  }

  /**
   * Deletes the account of the current user: the user, the tenants only they belong to with all
   * their data, and the login account. Shared tenants stay with their other members.
   */
  @DeleteMapping("/me")
  public AccountDeletionResponseDto deleteCurrentUser() {
    User user = userService.getCurrentUser();
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown user");
    }
    try {
      return new AccountDeletionResponseDto(accountDeletionService.deleteAccount(user));
    } catch (IllegalStateException e) {
      log.error("Account deletion of user id={} failed: {}", user.getId(), e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "The login account could not be deleted. Nothing was deleted.");
    }
  }

  private UserResponseDto convertToDto(User user) {
    return entityMapper.toDto(user);
  }
}
