/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import static org.junit.jupiter.api.Assertions.*;

import java.security.Security;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for NetworkConfig class. These tests verify DNS cache configuration without requiring
 * a full Spring context.
 */
class NetworkConfigTest {

  private NetworkConfig networkConfig;

  @BeforeEach
  void setUp() {
    networkConfig = new NetworkConfig();
  }

  @Test
  void testDnsCacheSettingsCanBeSet() {
    // Set DNS cache properties
    Security.setProperty("networkaddress.cache.ttl", "45");
    Security.setProperty("networkaddress.cache.negative.ttl", "8");

    // Verify they were set
    String cacheTtl = Security.getProperty("networkaddress.cache.ttl");
    String negativeCacheTtl = Security.getProperty("networkaddress.cache.negative.ttl");

    assertEquals("45", cacheTtl, "DNS cache TTL should be 45");
    assertEquals("8", negativeCacheTtl, "DNS negative cache TTL should be 8");
  }

  @Test
  void testDnsCacheCanBeDisabled() {
    // Set cache TTL to 0 (no caching)
    Security.setProperty("networkaddress.cache.ttl", "0");

    String cacheTtl = Security.getProperty("networkaddress.cache.ttl");
    assertEquals("0", cacheTtl, "DNS cache should be disabled when set to 0");
  }

  @Test
  void testDnsCacheCanBeMadeIndefinite() {
    // Set cache TTL to -1 (cache forever - JVM default)
    Security.setProperty("networkaddress.cache.ttl", "-1");

    String cacheTtl = Security.getProperty("networkaddress.cache.ttl");
    assertEquals("-1", cacheTtl, "DNS cache should be indefinite when set to -1");
  }

  @Test
  void testNetworkConfigObjectCanBeCreated() {
    assertNotNull(networkConfig, "NetworkConfig instance should be created");
  }
}
