/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "uptime_stats",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_monitor_period",
          columnNames = {"monitor_id", "periodType", "periodStart"})
    },
    indexes = {
      @Index(name = "idx_stats_monitor_period", columnList = "monitor_id, periodType"),
      @Index(name = "idx_stats_calculated", columnList = "calculatedAt"),
      @Index(name = "idx_stats_tenant", columnList = "tenantId")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UptimeStats {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "monitor_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_stats_monitor"))
  private Monitor monitor;

  @Column(name = "tenant_id", nullable = false)
  private Integer tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "tenant_id",
      nullable = false,
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_stats_tenant"))
  private Tenant tenant;

  @Enumerated(EnumType.STRING)
  @Column(name = "period_type", nullable = false)
  private PeriodType periodType;

  @Column(name = "period_start", nullable = false)
  private LocalDateTime periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDateTime periodEnd;

  @Column(name = "total_checks", nullable = false)
  private Integer totalChecks;

  @Column(name = "successful_checks", nullable = false)
  private Integer successfulChecks;

  @Column(name = "uptime_percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal uptimePercentage;

  @Column(name = "min_response_time_ms")
  private Integer minResponseTimeMs;

  @Column(name = "max_response_time_ms")
  private Integer maxResponseTimeMs;

  @Column(name = "avg_response_time_ms")
  private Integer avgResponseTimeMs;

  @Column(name = "p99_response_time_ms")
  private Integer p99ResponseTimeMs;

  @Column(name = "response_time_data", columnDefinition = "LONGTEXT")
  private String responseTimeData;

  @Column(name = "status_change_data", columnDefinition = "LONGTEXT")
  private String statusChangeData;

  @Column(name = "calculated_at", nullable = false)
  @Builder.Default
  private LocalDateTime calculatedAt = LocalDateTime.now();

  @AllArgsConstructor
  @Getter
  public enum PeriodType {
    SEVEN_DAYS("7d"),
    NINETY_DAYS("90d"),
    THREE_SIXTY_FIVE_DAYS("365d");

    private final String value;
  }

  @PrePersist
  protected void onCreate() {
    if (calculatedAt == null) {
      calculatedAt = LocalDateTime.now();
    }
  }
}
