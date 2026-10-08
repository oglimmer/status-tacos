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

/** One install of the iOS app that can get push notifications (an APNs device token). */
@Entity
@Table(name = "push_devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushDevice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private User user;

  /** Hex APNs device token. */
  @Column(name = "token", nullable = false, unique = true, length = 200)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(name = "environment", nullable = false, length = 20)
  private Environment environment;

  @Column(name = "device_name", length = 100)
  private String deviceName;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** Last registration by the app. The app registers on every start. */
  @Column(name = "last_seen_at", nullable = false)
  private LocalDateTime lastSeenAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    if (lastSeenAt == null) {
      lastSeenAt = now;
    }
  }

  /** APNs environment of the token: debug builds get sandbox tokens. */
  public enum Environment {
    SANDBOX,
    PRODUCTION
  }
}
