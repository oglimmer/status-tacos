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
    name = "alert_history",
    indexes = {
      @Index(name = "idx_alert_monitor_sent", columnList = "monitor_id, sentAt"),
      @Index(name = "idx_alert_sent", columnList = "sentAt"),
      @Index(name = "idx_alert_tenant", columnList = "tenantId")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlertHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "monitor_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_alert_monitor"))
  private Monitor monitor;

  @Column(name = "tenant_id", nullable = false)
  private Integer tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "tenant_id",
      nullable = false,
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_alert_tenant"))
  private Tenant tenant;

  @Enumerated(EnumType.STRING)
  @Column(name = "alert_type", nullable = false)
  private AlertType alertType;

  @Column(name = "sent_at", nullable = false)
  @Builder.Default
  private LocalDateTime sentAt = LocalDateTime.now();

  @Column(name = "email_sent_to", nullable = false, length = 320)
  private String emailSentTo;

  public enum AlertType {
    down,
    up,
    slow_response
  }

  @PrePersist
  protected void onCreate() {
    if (sentAt == null) {
      sentAt = LocalDateTime.now();
    }
  }
}
