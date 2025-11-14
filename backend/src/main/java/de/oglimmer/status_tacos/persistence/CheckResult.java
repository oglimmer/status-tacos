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
    name = "check_results",
    indexes = {
      @Index(name = "idx_check_monitor_time", columnList = "monitor_id, checkedAt"),
      @Index(name = "idx_check_time", columnList = "checkedAt"),
      @Index(name = "idx_check_tenant", columnList = "tenantId")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CheckResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "monitor_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_check_monitor"))
  private Monitor monitor;

  @Column(name = "tenant_id", nullable = false)
  private Integer tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "tenant_id",
      nullable = false,
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_check_tenant"))
  private Tenant tenant;

  @Column(name = "checked_at", nullable = false)
  @Builder.Default
  private LocalDateTime checkedAt = LocalDateTime.now();

  @Column(name = "status_code")
  private Integer statusCode;

  @Column(name = "response_time_ms")
  private Integer responseTimeMs;

  @Column(name = "is_up", nullable = false)
  private Boolean isUp;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @PrePersist
  protected void onCreate() {
    if (checkedAt == null) {
      checkedAt = LocalDateTime.now();
    }
  }
}
