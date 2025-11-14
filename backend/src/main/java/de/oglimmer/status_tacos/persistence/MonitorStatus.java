/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "monitor_status",
    indexes = {
      @Index(name = "idx_status_current", columnList = "currentStatus"),
      @Index(name = "idx_status_tenant", columnList = "tenantId")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MonitorStatus {

  @Id
  @Column(name = "monitor_id")
  @EqualsAndHashCode.Include
  private Integer monitorId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "monitor_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_status_monitor"))
  //    @MapsId // according to official documentation this should be here, but this also makes
  // problems, as it tries to update the monitorId if both fields are set and the monitor is
  // detached
  private Monitor monitor;

  @Column(name = "tenant_id", nullable = false)
  private Integer tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "tenant_id",
      nullable = false,
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_status_tenant"))
  private Tenant tenant;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_status", nullable = false)
  private StatusType currentStatus;

  @Column(name = "last_checked_at")
  private LocalDateTime lastCheckedAt;

  @Column(name = "last_up_at")
  private LocalDateTime lastUpAt;

  @Column(name = "last_down_at")
  private LocalDateTime lastDownAt;

  @Column(name = "consecutive_failures", nullable = false)
  @Builder.Default
  private Integer consecutiveFailures = 0;

  @Column(name = "last_response_time_ms")
  private Integer lastResponseTimeMs;

  @Column(name = "last_status_code")
  private Integer lastStatusCode;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public enum StatusType {
    up,
    down
  }

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
