/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.testdata;

import de.oglimmer.status_tacos.persistence.*;
import de.oglimmer.status_tacos.repository.*;
import de.oglimmer.status_tacos.service.UptimeStatsService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test data generator for manual UI testing.
 *
 * <p>This class generates realistic test data for monitors, check results, and uptime statistics
 * covering different timeframes (24h, 7d, 90d, 365d).
 *
 * <p>To run this test: 1. Remove @Disabled annotation 2. Run the generateTestData() method 3.
 * Re-add @Disabled annotation to prevent accidental execution
 *
 * <p>Note: This will add data to your database - use on test/dev environments only!
 */
@SpringBootTest
@ActiveProfiles("test-generation")
@Disabled("Manual test data generation - enable only when needed")
public class UptimeTestDataGenerator {

  @Autowired private TenantRepository tenantRepository;

  @Autowired private MonitorRepository monitorRepository;

  @Autowired private CheckResultRepository checkResultRepository;

  @Autowired private UptimeStatsService uptimeStatsService;

  private final Random random = new Random(42); // Fixed seed for reproducible results

  @Test
  //    @Transactional
  public void generateTestData() {
    System.out.println("🚀 Starting test data generation...");

    // Create test tenant
    Tenant testTenant = createTestTenant();

    // Create different types of monitors for comprehensive testing
    List<Monitor> monitors = createTestMonitors(testTenant);

    // Generate check results for different timeframes
    LocalDateTime now = LocalDateTime.now();

    for (Monitor monitor : monitors) {
      System.out.println("📊 Generating data for monitor: " + monitor.getName());

      // Generate 365 days of data (includes all timeframes)
      generateCheckResults(monitor, now.minusDays(365), now);

      // Calculate uptime statistics for all periods
      uptimeStatsService.calculateAndSaveUptimeStats(testTenant.getId());
    }

    System.out.println("✅ Test data generation completed!");
    System.out.println("📈 Generated data for " + monitors.size() + " monitors");
    System.out.println("⏰ Timeframes covered: 24h, 7d, 90d, 365d");
    System.out.println("🎯 Ready for manual UI testing!");
  }

  private Tenant createTestTenant() {
    Tenant tenant =
        Tenant.builder()
            .name("Test Tenant")
            .code("TEST001")
            .description("Generated tenant for UI testing")
            .isActive(true)
            .build();

    return tenantRepository.save(tenant);
  }

  private List<Monitor> createTestMonitors(Tenant tenant) {
    List<Monitor> monitors = new ArrayList<>();

    // Monitor 1: Highly reliable service (99.9% uptime)
    monitors.add(
        createMonitor(
            tenant,
            "Production API",
            "https://api.example.com/health",
            "Highly reliable production API endpoint",
            0.999));

    // Monitor 2: Moderately reliable service (95% uptime)
    monitors.add(
        createMonitor(
            tenant,
            "Beta Service",
            "https://beta.example.com/status",
            "Beta environment with occasional issues",
            0.95));

    // Monitor 3: Unreliable service (85% uptime)
    monitors.add(
        createMonitor(
            tenant,
            "Legacy System",
            "https://legacy.example.com/ping",
            "Legacy system with frequent downtime",
            0.85));

    // Monitor 4: Recently improved service (was bad, now good)
    monitors.add(
        createMonitor(
            tenant,
            "Improved Service",
            "https://improved.example.com/health",
            "Service that was recently improved",
            0.98));

    // Monitor 5: Recently degraded service (was good, now bad)
    monitors.add(
        createMonitor(
            tenant,
            "Degraded Service",
            "https://degraded.example.com/status",
            "Service that recently started having issues",
            0.80));

    return monitors;
  }

  // Map to store reliability data for each monitor
  private final java.util.Map<Integer, Double> monitorReliability = new java.util.HashMap<>();

  private Monitor createMonitor(
      Tenant tenant, String name, String url, String description, double baseReliability) {
    Monitor monitor =
        Monitor.builder()
            .name(name + " (" + description + ")")
            .url(url)
            .tenant(tenant)
            .tenantId(tenant.getId())
            .state(MonitorState.ACTIVE)
            .build();

    monitor = monitorRepository.save(monitor);

    // Store reliability for use in check result generation
    monitorReliability.put(monitor.getId(), baseReliability);

    return monitor;
  }

  private void generateCheckResults(Monitor monitor, LocalDateTime start, LocalDateTime end) {
    double baseReliability = monitorReliability.get(monitor.getId());
    LocalDateTime current = start;

    // Generate check every 5 minutes on average (with some variation)
    while (current.isBefore(end)) {
      // Determine if this check should be up or down
      boolean isUp = determineUpStatus(monitor, current, baseReliability);

      // Generate realistic response times
      Integer responseTime = generateResponseTime(isUp);
      Integer statusCode = isUp ? generateSuccessStatusCode() : generateErrorStatusCode();
      String errorMessage = isUp ? null : generateErrorMessage();

      CheckResult checkResult =
          CheckResult.builder()
              .monitor(monitor)
              .tenantId(monitor.getTenant().getId())
              .statusCode(statusCode)
              .responseTimeMs(responseTime)
              .isUp(isUp)
              .errorMessage(errorMessage)
              .checkedAt(current)
              .build();

      checkResultRepository.save(checkResult);

      // Next check in 3-7 minutes (simulating real monitoring intervals)
      current = current.plusMinutes(3 + random.nextInt(5));
    }
  }

  private boolean determineUpStatus(
      Monitor monitor, LocalDateTime checkTime, double baseReliability) {
    // Add some patterns to make data more realistic
    double reliability = baseReliability;

    // Simulate different patterns based on monitor name
    String monitorName = monitor.getName().toLowerCase();

    if (monitorName.contains("improved")) {
      // Service was bad 30+ days ago, improved recently
      long daysAgo = java.time.Duration.between(checkTime, LocalDateTime.now()).toDays();
      if (daysAgo > 30) {
        reliability = 0.70; // Was unreliable
      } else if (daysAgo > 7) {
        reliability = 0.85; // Improving
      } else {
        reliability = 0.98; // Now very reliable
      }
    } else if (monitorName.contains("degraded")) {
      // Service was good, recently started having issues
      long daysAgo = java.time.Duration.between(checkTime, LocalDateTime.now()).toDays();
      if (daysAgo > 14) {
        reliability = 0.98; // Was very reliable
      } else if (daysAgo > 3) {
        reliability = 0.90; // Starting to degrade
      } else {
        reliability = 0.75; // Now problematic
      }
    }

    // Add some time-based patterns
    int hour = checkTime.getHour();

    // Simulate maintenance windows (2-4 AM has higher failure rate)
    if (hour >= 2 && hour <= 4) {
      reliability *= 0.8;
    }

    // Simulate peak load issues (9-11 AM and 2-4 PM)
    if ((hour >= 9 && hour <= 11) || (hour >= 14 && hour <= 16)) {
      reliability *= 0.95;
    }

    // Add some randomness for realistic outages
    return random.nextDouble() < reliability;
  }

  private Integer generateResponseTime(boolean isUp) {
    if (!isUp) {
      // Failed requests have no response time or very high timeout
      return random.nextBoolean() ? null : 30000 + random.nextInt(30000);
    }

    // Successful requests have varied response times
    // 70% fast (50-200ms), 20% medium (200-1000ms), 10% slow (1-5s)
    double percentile = random.nextDouble();

    if (percentile < 0.7) {
      return 50 + random.nextInt(150); // 50-200ms
    } else if (percentile < 0.9) {
      return 200 + random.nextInt(800); // 200-1000ms
    } else {
      return 1000 + random.nextInt(4000); // 1-5s
    }
  }

  private Integer generateSuccessStatusCode() {
    // Mostly 200, sometimes other success codes
    double rand = random.nextDouble();
    if (rand < 0.85) return 200;
    if (rand < 0.95) return 201;
    if (rand < 0.98) return 204;
    return 202;
  }

  private Integer generateErrorStatusCode() {
    // Various error codes
    int[] errorCodes = {400, 401, 403, 404, 429, 500, 502, 503, 504, 520};
    return errorCodes[random.nextInt(errorCodes.length)];
  }

  private String generateErrorMessage() {
    String[] errorMessages = {
      "Connection timeout",
      "Service unavailable",
      "Internal server error",
      "Bad gateway",
      "Rate limit exceeded",
      "Authentication failed",
      "Resource not found",
      "Database connection failed",
      "Memory limit exceeded",
      "Network unreachable"
    };
    return errorMessages[random.nextInt(errorMessages.length)];
  }
}
