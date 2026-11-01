/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.time.LocalDateTime;

/**
 * The answer to the heartbeat. The peer monitor of the other server has the body regex
 * "status":"ok", so cannot-alert makes it DOWN.
 *
 * @param status ok or cannot-alert
 * @param since when this server stopped being able to alert. Null when ok.
 * @param reason why it cannot alert. Null when ok.
 */
public record PeerHeartbeatDto(String status, LocalDateTime since, String reason) {

  public static final String OK = "ok";
  public static final String CANNOT_ALERT = "cannot-alert";
}
