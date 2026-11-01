/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.ApiTokenPrincipal;
import de.oglimmer.status_tacos.config.PeerConfig;
import de.oglimmer.status_tacos.dto.ApiTokenResponseDto;
import de.oglimmer.status_tacos.persistence.ApiToken;
import de.oglimmer.status_tacos.persistence.ApiTokenScope;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.ApiTokenRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
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
 *
 * <p>A token has a scope: a GRAFANA token reads /prom, a PEER token lets the other Status Tacos
 * server call /peer. A PEER token works only while its user is a member of the peer tenant.
 */
@Service
@RequiredArgsConstructor
public class ApiTokenService {

  static final String TOKEN_START = "stt_";
  static final int MAX_TOKENS_PER_USER = 20;
  private static final int PREFIX_LENGTH = TOKEN_START.length() + 8;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ApiTokenRepository apiTokenRepository;
  private final UserRepository userRepository;
  private final PeerConfig peerConfig;

  public List<ApiTokenResponseDto> list(User user) {
    return apiTokenRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
        .map(token -> toDto(token, null))
        .toList();
  }

  /** The answer holds the full token. It is the only time the user sees it. */
  @Transactional
  public ApiTokenResponseDto create(User user, String name) {
    return create(user, name, ApiTokenScope.GRAFANA);
  }

  /**
   * @throws IllegalArgumentException for a PEER token when the servers do not work together or the
   *     user is no member of the peer tenant
   */
  @Transactional
  public ApiTokenResponseDto create(User user, String name, ApiTokenScope scope) {
    ApiTokenScope tokenScope = scope != null ? scope : ApiTokenScope.GRAFANA;
    if (tokenScope == ApiTokenScope.PEER && !isPeerMember(user.getId())) {
      throw new IllegalArgumentException(
          "A peer token needs monitor.peer.tenant-id on the server, and you must be a member of"
              + " that tenant.");
    }
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
                .scope(tokenScope)
                .build());
    return toDto(saved, token);
  }

  @Transactional
  public boolean delete(User user, Long id) {
    return apiTokenRepository.deleteByIdAndUserId(id, user.getId()) > 0;
  }

  /** The user of a GRAFANA token. Empty for an unknown token or an inactive user. */
  @Transactional
  public Optional<ApiTokenPrincipal> authenticate(String token) {
    return authenticate(token, ApiTokenScope.GRAFANA);
  }

  /**
   * The user of the token, if it has this scope. Empty for an unknown token, another scope or an
   * inactive user. A PEER token is also empty when its user left the peer tenant.
   */
  @Transactional
  public Optional<ApiTokenPrincipal> authenticate(String token, ApiTokenScope scope) {
    if (token == null || !token.startsWith(TOKEN_START)) {
      return Optional.empty();
    }
    Optional<ApiToken> found =
        apiTokenRepository
            .findByTokenHash(hash(token))
            .filter(apiToken -> Boolean.TRUE.equals(apiToken.getUser().getIsActive()))
            .filter(apiToken -> apiToken.getScope() == scope)
            .filter(
                apiToken ->
                    scope != ApiTokenScope.PEER || isPeerMember(apiToken.getUser().getId()));
    found.ifPresent(this::touch);
    return found.map(
        apiToken ->
            new ApiTokenPrincipal(
                apiToken.getId(),
                apiToken.getUser().getId(),
                apiToken.getUser().getOidcSubject(),
                apiToken.getScope()));
  }

  private boolean isPeerMember(Integer userId) {
    return peerConfig.isEnabled()
        && userRepository.isMemberOfTenant(userId, peerConfig.getTenantId());
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
        token.getScope(),
        token.getCreatedAt(),
        token.getLastUsedAt(),
        fullToken);
  }
}
