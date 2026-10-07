/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import java.time.LocalDateTime;

/** The fields of a check result that the 24h view needs. */
public record CheckPoint(LocalDateTime checkedAt, Boolean isUp, Integer responseTimeMs) {}
