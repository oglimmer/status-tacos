/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import de.oglimmer.status_tacos.validation.MultipleOf15;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "monitors",
    indexes = {
      @Index(name = "idx_monitors_state", columnList = "state"),
      @Index(name = "idx_monitors_created", columnList = "createdAt"),
      @Index(name = "idx_monitors_tenant", columnList = "tenantId"),
      @Index(name = "idx_monitors_alerting_threshold", columnList = "alertingThreshold")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Monitor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Integer id;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(name = "tenant_id", nullable = false)
  private Integer tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "tenant_id",
      nullable = false,
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_monitor_tenant"))
  private Tenant tenant;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  @Builder.Default
  private MonitorState state = MonitorState.ACTIVE;

  @Column(name = "http_headers", columnDefinition = "JSON")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, String> httpHeaders;

  @Column(name = "status_code_regex", length = 500)
  @Builder.Default
  private String statusCodeRegex = "^[23]\\d{2}$";

  @Column(name = "response_body_regex", length = 1000)
  private String responseBodyRegex;

  @Column(name = "prometheus_key", length = 255)
  private String prometheusKey;

  @Column(name = "prometheus_min_value")
  private Double prometheusMinValue;

  @Column(name = "prometheus_max_value")
  private Double prometheusMaxValue;

  @Column(name = "alerting_threshold", nullable = false)
  @Min(value = 15, message = "Alerting threshold must be at least 15 seconds")
  @MultipleOf15(message = "Alerting threshold must be a multiple of 15 seconds")
  @Builder.Default
  private Integer alertingThreshold = 30;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "monitor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<CheckResult> checkResults;

  @OneToOne(mappedBy = "monitor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private MonitorStatus monitorStatus;

  @OneToMany(mappedBy = "monitor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<AlertHistory> alertHistories;

  @OneToMany(mappedBy = "monitor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<UptimeStats> uptimeStats;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
