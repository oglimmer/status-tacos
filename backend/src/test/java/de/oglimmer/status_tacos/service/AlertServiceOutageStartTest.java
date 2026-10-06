/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.oglimmer.status_tacos.config.TestSecurityConfig;
import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
class AlertServiceOutageStartTest {

  // MariaDB stores checked_at in whole seconds
  private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 10, 0, 0);

  @Autowired private AlertService alertService;
  @Autowired private CheckResultRepository checkResultRepository;
  @Autowired private MonitorRepository monitorRepository;

  private Monitor monitor;

  @BeforeEach
  void setUp() {
    monitor =
        monitorRepository.save(
            Monitor.builder().name("Shop").url("https://shop.example.com").tenantId(1).build());
  }

  @Test
  void outageStartsAtFirstFailedCheckAfterLastSuccess() {
    save(true, T0);
    save(false, T0.plusSeconds(15)); // earlier outage
    save(true, T0.plusSeconds(30));
    CheckResult firstDown = save(false, T0.plusSeconds(45));
    save(false, T0.plusSeconds(60));
    CheckResult current = save(false, T0.plusSeconds(75));

    assertEquals(firstDown.getCheckedAt(), alertService.findOutageStart(current));
  }

  @Test
  void upCheckInSameSecondAsLastFailureStillFindsTheOutage() {
    save(true, T0);
    CheckResult firstDown = save(false, T0.plusSeconds(15));
    save(false, T0.plusSeconds(30));
    // The recovering check is evaluated before anything later exists; same second as the failure
    CheckResult current = save(true, T0.plusSeconds(30));

    assertEquals(firstDown.getCheckedAt(), alertService.findOutageStart(current));
  }

  @Test
  void monitorThatWasNeverUpIsDownSinceItsFirstCheck() {
    CheckResult first = save(false, T0);
    CheckResult current = save(false, T0.plusSeconds(15));

    assertEquals(first.getCheckedAt(), alertService.findOutageStart(current));
  }

  @Test
  void noFailedCheckMeansNoOutage() {
    save(true, T0);
    CheckResult current = save(true, T0.plusSeconds(15));

    assertNull(alertService.findOutageStart(current));
  }

  private CheckResult save(boolean up, LocalDateTime checkedAt) {
    return checkResultRepository.save(
        CheckResult.builder()
            .monitor(monitor)
            .tenantId(1)
            .isUp(up)
            .statusCode(up ? 200 : 503)
            .checkedAt(checkedAt)
            .build());
  }
}
