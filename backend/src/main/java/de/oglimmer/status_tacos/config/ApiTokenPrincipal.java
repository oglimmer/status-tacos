/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

/** The signed-in user of a request with an API token. */
public record ApiTokenPrincipal(Long tokenId, Integer userId, String oidcSubject) {}
