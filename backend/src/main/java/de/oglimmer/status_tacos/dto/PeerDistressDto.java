/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

/**
 * "Check me now": the sender can no longer alert, or can again. The receiver checks its peer
 * monitor at once, so it does not wait for the next check.
 *
 * @param status ok or cannot-alert, as in the heartbeat
 */
public record PeerDistressDto(String status, String reason) {}
