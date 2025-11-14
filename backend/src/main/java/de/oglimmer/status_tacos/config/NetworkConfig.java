/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import jakarta.annotation.PostConstruct;
import java.security.Security;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for network-level settings, particularly DNS caching behavior.
 *
 * <p>By default, the JVM caches DNS lookups indefinitely, which can be problematic for long-running
 * processes where DNS entries may change (e.g., load balancers, cloud services with dynamic IPs,
 * etc.).
 *
 * <p>This configuration allows tuning the DNS cache TTL to ensure the application picks up DNS
 * changes without requiring a restart.
 */
@Configuration
@Slf4j
public class NetworkConfig {

  /**
   * Time-to-live for successful DNS lookups in seconds. Default: 60 seconds
   *
   * <p>Set to 0 to disable caching entirely (not recommended for production). Set to -1 to cache
   * forever (JVM default behavior).
   */
  @Value("${monitor.network.dns.cache-ttl:60}")
  private int dnsCacheTtl;

  /**
   * Time-to-live for failed DNS lookups in seconds. Default: 10 seconds
   *
   * <p>This prevents excessive retries to non-existent hosts.
   */
  @Value("${monitor.network.dns.negative-cache-ttl:10}")
  private int dnsNegativeCacheTtl;

  @PostConstruct
  public void configureDnsCache() {
    log.info("Configuring DNS cache settings:");
    log.info("  - Positive cache TTL: {} seconds", dnsCacheTtl);
    log.info("  - Negative cache TTL: {} seconds", dnsNegativeCacheTtl);

    // Set the DNS cache TTL for successful lookups
    Security.setProperty("networkaddress.cache.ttl", String.valueOf(dnsCacheTtl));

    // Set the DNS cache TTL for failed lookups
    Security.setProperty("networkaddress.cache.negative.ttl", String.valueOf(dnsNegativeCacheTtl));

    // Verify the settings were applied
    String actualTtl = Security.getProperty("networkaddress.cache.ttl");
    String actualNegativeTtl = Security.getProperty("networkaddress.cache.negative.ttl");

    log.info("DNS cache configuration applied successfully:");
    log.info("  - Actual positive cache TTL: {} seconds", actualTtl);
    log.info("  - Actual negative cache TTL: {} seconds", actualNegativeTtl);
  }
}
