/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Sign in with Apple, brokered by Keycloak. Apple requires that an app with Sign in with Apple
 * revokes the user's Apple tokens when the user deletes their account. The values are the same as
 * in the Apple identity provider in Keycloak.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.apple-sign-in")
public class AppleSignInConfig {

  /** Off: account deletion does not revoke Apple tokens. Needs Keycloak admin access when on. */
  private boolean enabled = false;

  /** Alias of the Apple identity provider in Keycloak. */
  private String identityProviderAlias = "apple";

  /** The Services ID, the client ID of the Apple identity provider in Keycloak. */
  private String clientId = "";

  /** Team ID of the Apple Developer account (10 characters). */
  private String teamId = "";

  /** Key ID of the .p8 key with "Sign in with Apple" (10 characters). */
  private String keyId = "";

  /** Content of the .p8 key file (PEM, "-----BEGIN PRIVATE KEY-----"). */
  private String privateKey = "";

  private String revokeUrl = "https://appleid.apple.com/auth/revoke";

  private Duration timeout = Duration.ofSeconds(10);
}
