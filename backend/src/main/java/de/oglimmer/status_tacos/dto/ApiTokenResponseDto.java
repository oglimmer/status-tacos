/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.time.LocalDateTime;

/**
 * @param prefix the start of the token, to tell the tokens apart
 * @param token the full token. Only set in the answer to the create request: the server does not
 *     store it.
 */
public record ApiTokenResponseDto(
    Long id,
    String name,
    String prefix,
    LocalDateTime createdAt,
    LocalDateTime lastUsedAt,
    String token) {}
