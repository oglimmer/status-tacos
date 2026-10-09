/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

/**
 * Result of an account deletion.
 *
 * @param loginAccountDeleted true if the login account at the identity provider was deleted too.
 *     False on an install without Keycloak admin access: the user must delete it there.
 */
public record AccountDeletionResponseDto(boolean loginAccountDeleted) {}
