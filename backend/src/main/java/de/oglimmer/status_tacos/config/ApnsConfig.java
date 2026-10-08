/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Apple Push Notification service (APNs) with token-based authentication. The key is a .p8 key of
 * the Apple Developer account with the "Apple Push Notifications service" capability.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.apns")
public class ApnsConfig {

  /** Off: iOS push alert contacts are kept, but no notification is sent. */
  private boolean enabled = false;

  /** Team ID of the Apple Developer account (10 characters). */
  private String teamId = "";

  /** Key ID of the .p8 key (10 characters). */
  private String keyId = "";

  /** Content of the .p8 key file (PEM, "-----BEGIN PRIVATE KEY-----"). */
  private String privateKey = "";

  /** Bundle ID of the iOS app, sent as apns-topic. */
  private String bundleId = "de.oglimmer.statustacos";

  private String productionUrl = "https://api.push.apple.com";
  private String sandboxUrl = "https://api.sandbox.push.apple.com";

  private Duration timeout = Duration.ofSeconds(10);

  /** How long APNs keeps trying to deliver to a device that is offline. */
  private Duration expiration = Duration.ofHours(1);
}
