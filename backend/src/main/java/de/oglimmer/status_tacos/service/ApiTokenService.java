/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.ApiTokenPrincipal;
import de.oglimmer.status_tacos.dto.ApiTokenResponseDto;
import de.oglimmer.status_tacos.persistence.ApiToken;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.ApiTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API tokens: a tool like Grafana sends one instead of a Keycloak sign-in. A token has the rights
 * of its user. The database stores only its SHA-256 hash.
 */
@Service
@RequiredArgsConstructor
public class ApiTokenService {

  static final String TOKEN_START = "stt_";
  static final int MAX_TOKENS_PER_USER = 20;
  private static final int PREFIX_LENGTH = TOKEN_START.length() + 8;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ApiTokenRepository apiTokenRepository;

  public List<ApiTokenResponseDto> list(User user) {
    return apiTokenRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
        .map(token -> toDto(token, null))
        .toList();
  }

  /** The answer holds the full token. It is the only time the user sees it. */
  @Transactional
  public ApiTokenResponseDto create(User user, String name) {
    if (apiTokenRepository.countByUserId(user.getId()) >= MAX_TOKENS_PER_USER) {
      throw new IllegalStateException(
          "You have " + MAX_TOKENS_PER_USER + " API tokens. Delete one to create a new one.");
    }
    byte[] random = new byte[32];
    RANDOM.nextBytes(random);
    String token = TOKEN_START + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    ApiToken saved =
        apiTokenRepository.save(
            ApiToken.builder()
                .user(user)
                .name(name.trim())
                .tokenHash(hash(token))
                .tokenPrefix(token.substring(0, PREFIX_LENGTH))
                .build());
    return toDto(saved, token);
  }

  @Transactional
  public boolean delete(User user, Long id) {
    return apiTokenRepository.deleteByIdAndUserId(id, user.getId()) > 0;
  }

  /** The user of the token. Empty for an unknown token or an inactive user. */
  @Transactional
  public Optional<ApiTokenPrincipal> authenticate(String token) {
    if (token == null || !token.startsWith(TOKEN_START)) {
      return Optional.empty();
    }
    Optional<ApiToken> found =
        apiTokenRepository
            .findByTokenHash(hash(token))
            .filter(apiToken -> Boolean.TRUE.equals(apiToken.getUser().getIsActive()));
    found.ifPresent(this::touch);
    return found.map(
        apiToken ->
            new ApiTokenPrincipal(
                apiToken.getId(), apiToken.getUser().getId(), apiToken.getUser().getOidcSubject()));
  }

  /** Grafana sends many requests. One write per minute is exact enough. */
  private void touch(ApiToken apiToken) {
    LocalDateTime now = LocalDateTime.now();
    if (apiToken.getLastUsedAt() == null
        || apiToken.getLastUsedAt().isBefore(now.minusMinutes(1))) {
      apiTokenRepository.updateLastUsedAt(apiToken.getId(), now);
    }
  }

  static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ApiTokenResponseDto toDto(ApiToken token, String fullToken) {
    return new ApiTokenResponseDto(
        token.getId(),
        token.getName(),
        token.getTokenPrefix(),
        token.getCreatedAt(),
        token.getLastUsedAt(),
        fullToken);
  }
}
