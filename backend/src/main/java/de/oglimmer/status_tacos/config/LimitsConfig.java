/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Limits for each user. A user with users.max_monitors set uses that value instead. */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.limits")
public class LimitsConfig {

  /** Monitors in all tenants of a user. 0 or less: no limit. The hosted instance sets 3. */
  private int maxMonitorsPerUser = 0;
}
