/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

/** What the other server saw. */
public record PeerCheckResponseDto(
    boolean up, Integer statusCode, Integer responseTimeMs, String errorMessage) {}
