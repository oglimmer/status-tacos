/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

  private final UserRepository userRepository;

  public User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }

    if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
      return null;
    }

    String subject = jwt.getSubject();
    if (subject == null || subject.isEmpty()) {
      return null;
    }

    Optional<User> existingUser = userRepository.findByOidcSubjectAndIsActiveTrue(subject);

    if (existingUser.isPresent()) {
      User user = existingUser.get();
      // Force initialization of lazy-loaded tenants collection
      user.getTenants().size();
      return user;
    }

    return null;
  }
}
