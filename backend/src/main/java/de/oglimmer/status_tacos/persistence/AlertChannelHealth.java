/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Whether this server can send alerts on one channel. In the database, not in memory: with two
 * replicas, either one sends the alerts and either one answers the heartbeat.
 */
@Entity
@Table(name = "alert_channel_health")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertChannelHealth {

  public enum Channel {
    EMAIL,
    PUSH,
    /** HTTP and Teams contacts. */
    WEBHOOK
  }

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "channel", length = 20)
  private Channel channel;

  /** Failed sends since the last successful one. */
  @Column(name = "consecutive_failures", nullable = false)
  @Builder.Default
  private Integer consecutiveFailures = 0;

  /** Hosts that failed since the last successful send, comma-separated (WEBHOOK only). */
  @Column(name = "failed_hosts", length = 1000)
  private String failedHosts;

  /** Set while the channel counts as failing. */
  @Column(name = "failing_since")
  private LocalDateTime failingSince;

  /** The last error. */
  @Column(name = "reason", length = 500)
  private String reason;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
