/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

/**
 * @param enabled the server sends push notifications (APNs is configured)
 * @param devices registered devices of the current user
 */
public record PushStatusResponseDto(boolean enabled, int devices) {}
