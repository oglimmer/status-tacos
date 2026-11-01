/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.time.LocalDateTime;

/**
 * The peer setup of this server, for the members of the peer tenant.
 *
 * @param setup OK, NO_PEER_MONITOR, MULTIPLE_PEER_MONITORS or NO_TOKEN. Not OK: the other server is
 *     not asked before DOWN alerts.
 * @param monitorId the peer monitor, if there is exactly one
 * @param canAlert false: this server cannot send alerts, its heartbeat says cannot-alert
 */
public record PeerStatusDto(
    String setup,
    Integer peerTenantId,
    Integer monitorId,
    String monitorName,
    boolean canAlert,
    LocalDateTime cannotAlertSince,
    String cannotAlertReason) {}
