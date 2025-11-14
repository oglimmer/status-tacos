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
    name = "cleanup_jobs",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_tenant_job_type",
          columnNames = {"tenantId", "jobType"})
    },
    indexes = {
      @Index(name = "idx_cleanup_last_run", columnList = "lastRunAt"),
      @Index(name = "idx_cleanup_tenant", columnList = "tenantId")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CleanupJob {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Integer id;

  @Column(name = "job_type", nullable = false, length = 50)
  private String jobType;

  @Column(name = "tenant_id", nullable = false)
  private Integer tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "tenant_id",
      nullable = false,
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_cleanup_tenant"))
  private Tenant tenant;

  @Column(name = "last_run_at", nullable = false)
  private LocalDateTime lastRunAt;

  @Column(name = "records_deleted", nullable = false)
  @Builder.Default
  private Integer recordsDeleted = 0;

  @Column(name = "execution_time_ms")
  private Integer executionTimeMs;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private JobStatus status = JobStatus.completed;

  public enum JobStatus {
    running,
    completed,
    failed
  }

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    if (lastRunAt == null) {
      lastRunAt = LocalDateTime.now();
    }
  }
}
