/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Two Status Tacos servers that work together. The peer tenant holds one monitor of the heartbeat
 * of the other server (https://other/api/peer/heartbeat with an "Authorization: Bearer" header).
 * That monitor gives the address and the token of the other server.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.peer")
public class PeerConfig {

  /** The tenant of the peer monitor. Null: the servers do not work together. */
  private Integer tenantId;

  /** How long to wait for the other server to check a monitor before a DOWN alert. */
  private Duration confirmTimeout = Duration.ofSeconds(12);

  /** Checks that the other server can ask for in one minute. More get HTTP 429. */
  private int maxChecksPerMinute = 120;

  public boolean isEnabled() {
    return tenantId != null;
  }

  public boolean isPeerTenant(Integer id) {
    return tenantId != null && tenantId.equals(id);
  }
}
