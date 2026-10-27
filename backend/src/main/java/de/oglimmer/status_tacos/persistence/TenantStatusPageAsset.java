/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** A file of the public status page of a tenant: a font or an image. */
@Entity
@Table(name = "tenant_status_page_assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TenantStatusPageAsset {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Integer tenantId;

  /** The file name. The CSS and the template use it as {@code asset:<name>}. */
  @Column(name = "name", nullable = false, length = 100)
  private String name;

  /** Set by the server from the file extension, never from the upload. */
  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "size", nullable = false)
  private Integer size;

  /** Hex. The URL and the ETag use it, so a new file gets past all caches. */
  @Column(name = "sha256", nullable = false, length = 64)
  private String sha256;

  /** MEDIUMBLOB in MariaDB. */
  @ToString.Exclude
  @Column(name = "data", nullable = false, length = 16 * 1024 * 1024)
  private byte[] data;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
