/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

/** This server can no longer send alerts on a channel, or can again. Published after the commit. */
public record AlertDeliveryHealthChanged(boolean canAlert) {}
