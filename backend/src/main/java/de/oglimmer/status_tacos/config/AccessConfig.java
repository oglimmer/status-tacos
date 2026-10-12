/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Who may use the backend. Empty list: everybody who has a valid token. With entries: only the
 * listed email + login provider pairs. The backend rejects all other tokens with 401.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.access")
public class AccessConfig {

  private List<AllowedLogin> allowedLogins = new ArrayList<>();

  /** Name of the token claim that holds the login provider (Keycloak identity provider alias). */
  private String providerClaim = "identity_provider";

  @Data
  public static class AllowedLogin {
    private String email = "";

    /** Alias of the identity provider, for example "google" or "apple". */
    private String provider = "";
  }
}
