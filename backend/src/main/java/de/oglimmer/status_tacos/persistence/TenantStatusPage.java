/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** The custom look of the public status page of a tenant. Null means the built-in default. */
@Entity
@Table(name = "tenant_status_pages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TenantStatusPage {

  @Id
  @Column(name = "tenant_id")
  @EqualsAndHashCode.Include
  private Integer tenantId;

  /** Mustache template of the page body. MEDIUMTEXT in MariaDB. */
  @Column(name = "html_template", length = 65536)
  private String htmlTemplate;

  @Column(name = "css", length = 65536)
  private String css;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
