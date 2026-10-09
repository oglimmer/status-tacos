/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Keycloak Admin REST API access, used to delete the login account when a user deletes their
 * account. The client is a confidential client of the app's realm with "Service accounts roles" on
 * and the client role realm-management/manage-users.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.keycloak-admin")
public class KeycloakAdminConfig {

  /**
   * Off: account deletion deletes only the data in this app. The login account at the identity
   * provider stays (for a self-hosted install with another OpenID Connect provider).
   */
  private boolean enabled = false;

  /** Keycloak base URL, without /realms. */
  private String baseUrl = "https://id.oglimmer.de";

  /** Realm of the app users. The admin client is in the same realm. */
  private String realm = "status-tacos";

  private String clientId = "";

  private String clientSecret = "";

  private Duration timeout = Duration.ofSeconds(10);
}
