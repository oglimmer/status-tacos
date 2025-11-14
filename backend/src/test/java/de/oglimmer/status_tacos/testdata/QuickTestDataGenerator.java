/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.testdata;

import de.oglimmer.status_tacos.persistence.*;
import de.oglimmer.status_tacos.repository.*;
import de.oglimmer.status_tacos.service.UptimeStatsService;
import java.time.LocalDateTime;
import java.util.Random;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Quick test data generator for rapid UI testing.
 *
 * <p>Generates minimal but sufficient test data for testing the timeframe switcher and uptime
 * statistics functionality.
 *
 * <p>This generates: - 1 test tenant - 3 monitors with different reliability patterns - Check
 * results for the last 7 days (sufficient for testing all timeframes) - Uptime statistics for all
 * periods
 *
 * <p>To run this test: 1. Remove @Disabled annotation 2. Run the generateQuickTestData() method 3.
 * Re-add @Disabled annotation
 */
@SpringBootTest
@ActiveProfiles("test-generation")
@Disabled("Manual test data generation - enable only when needed")
public class QuickTestDataGenerator {

  @Autowired private TenantRepository tenantRepository;

  @Autowired private MonitorRepository monitorRepository;

  @Autowired private CheckResultRepository checkResultRepository;

  @Autowired private UptimeStatsService uptimeStatsService;

  private final Random random = new Random(123);

  @Test
  //    @Transactional
  public void generateQuickTestData() {
    System.out.println("⚡ Starting quick test data generation...");

    // Create test tenant
    Tenant tenant = createTestTenant();

    // Create 3 test monitors
    Monitor reliableMonitor =
        createMonitor(tenant, "Reliable Service", "https://reliable.test.com", 0.98);
    Monitor unstableMonitor =
        createMonitor(tenant, "Unstable Service", "https://unstable.test.com", 0.85);
    Monitor flakyMonitor = createMonitor(tenant, "Flaky Service", "https://flaky.test.com", 0.75);

    // Generate 7 days of data (covers 24h, 7d timeframes)
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime sevenDaysAgo = now.minusDays(7);

    generateSimpleCheckResults(reliableMonitor, sevenDaysAgo, now, 0.98);
    generateSimpleCheckResults(unstableMonitor, sevenDaysAgo, now, 0.85);
    generateSimpleCheckResults(flakyMonitor, sevenDaysAgo, now, 0.75);

    // Calculate uptime statistics
    uptimeStatsService.calculateAndSaveUptimeStats(tenant.getId());

    System.out.println("✅ Quick test data generation completed!");
    System.out.println("📊 Created 3 monitors with 7 days of data");
    System.out.println("🎯 Ready for timeframe switcher testing!");
  }

  private Tenant createTestTenant() {
    // Check if test tenant already exists
    return tenantRepository
        .findByCode("TESTUI")
        .orElseGet(
            () -> {
              Tenant tenant =
                  Tenant.builder()
                      .name("UI Test Tenant")
                      .code("TESTUI")
                      .description("Quick test tenant for UI testing")
                      .isActive(true)
                      .build();
              return tenantRepository.save(tenant);
            });
  }

  private Monitor createMonitor(Tenant tenant, String name, String url, double reliability) {
    // Check if monitor already exists
    return monitorRepository
        .findByNameAndTenantId(name, tenant.getId())
        .orElseGet(
            () -> {
              Monitor monitor =
                  Monitor.builder()
                      .name(name)
                      .url(url)
                      .tenant(tenant)
                      .tenantId(tenant.getId())
                      .state(MonitorState.ACTIVE)
                      .build();
              return monitorRepository.save(monitor);
            });
  }

  private void generateSimpleCheckResults(
      Monitor monitor, LocalDateTime start, LocalDateTime end, double reliability) {
    LocalDateTime current = start;

    // Generate checks every 5 minutes
    while (current.isBefore(end)) {
      boolean isUp = random.nextDouble() < reliability;

      CheckResult checkResult =
          CheckResult.builder()
              .monitor(monitor)
              .tenantId(monitor.getTenant().getId())
              .statusCode(isUp ? 200 : 500)
              .responseTimeMs(isUp ? 100 + random.nextInt(400) : null)
              .isUp(isUp)
              .errorMessage(isUp ? null : "Service unavailable")
              .checkedAt(current)
              .build();

      checkResultRepository.save(checkResult);
      current = current.plusMinutes(5);
    }

    System.out.println(
        "📈 Generated data for "
            + monitor.getName()
            + " (reliability: "
            + (int) (reliability * 100)
            + "%)");
  }
}
