/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.time.LocalDateTime;

/**
 * What a Teams card shows. Fields that do not apply to a {@link Kind} are null.
 *
 * @param reason why the check failed, e.g. "HTTP 503 response" or "Network error: ..."
 * @param checkedAt when the check that triggered the alert ran
 * @param downSince the first failed check of the current or just ended outage
 * @param scope for TEST: which monitors the contact is alerted for
 */
public record TeamsAlert(
    Kind kind,
    String monitorName,
    String monitorUrl,
    String tenantName,
    Integer statusCode,
    String reason,
    Integer responseTimeMs,
    LocalDateTime checkedAt,
    LocalDateTime downSince,
    Integer alertingThresholdSeconds,
    String contactName,
    String scope) {

  public enum Kind {
    DOWN,
    UP,
    TEST
  }
}
