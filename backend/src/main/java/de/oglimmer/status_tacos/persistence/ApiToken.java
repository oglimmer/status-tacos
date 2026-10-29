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

/** A token that a tool (for example Grafana) sends instead of a Keycloak sign-in. */
@Entity
@Table(name = "api_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private User user;

  /** For example "Grafana at home". */
  @Column(name = "name", nullable = false, length = 100)
  private String name;

  /** Hex SHA-256 of the token. The token itself is not stored. */
  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  /** The start of the token, so the user can tell the tokens apart. */
  @Column(name = "token_prefix", nullable = false, length = 20)
  private String tokenPrefix;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
