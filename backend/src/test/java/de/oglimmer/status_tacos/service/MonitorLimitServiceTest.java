/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import de.oglimmer.status_tacos.config.LimitsConfig;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorLimitServiceTest {

  private static final Set<Integer> TENANT_IDS = Set.of(1, 2);

  @Mock private MonitorRepository monitorRepository;

  private LimitsConfig limitsConfig;
  private MonitorLimitService monitorLimitService;

  @BeforeEach
  void setUp() {
    limitsConfig = new LimitsConfig();
    limitsConfig.setMaxMonitorsPerUser(3);
    monitorLimitService = new MonitorLimitService(limitsConfig, monitorRepository);
  }

  @Test
  void noLimitByDefault() {
    assertThat(
            new MonitorLimitService(new LimitsConfig(), monitorRepository)
                .maxMonitorsFor(new User()))
        .isNull();
  }

  @Test
  void configuredLimitApplies() {
    assertThat(monitorLimitService.maxMonitorsFor(new User())).isEqualTo(3);
  }

  @Test
  void userValueOverridesDefault() {
    User user = User.builder().maxMonitors(10).build();
    assertThat(monitorLimitService.maxMonitorsFor(user)).isEqualTo(10);
  }

  @Test
  void zeroMeansNoLimit() {
    limitsConfig.setMaxMonitorsPerUser(0);
    assertThat(monitorLimitService.maxMonitorsFor(new User())).isNull();
    assertThat(monitorLimitService.maxMonitorsFor(User.builder().maxMonitors(5).build()))
        .isEqualTo(5);
    assertThat(monitorLimitService.maxMonitorsFor(User.builder().maxMonitors(0).build())).isNull();
  }

  @Test
  void createAllowedBelowLimit() {
    when(monitorRepository.countByTenantIdIn(TENANT_IDS)).thenReturn(2L);
    assertThatCode(() -> monitorLimitService.checkCanCreateMonitor(new User(), TENANT_IDS))
        .doesNotThrowAnyException();
  }

  @Test
  void createRejectedAtLimit() {
    when(monitorRepository.countByTenantIdIn(TENANT_IDS)).thenReturn(3L);
    assertThatThrownBy(() -> monitorLimitService.checkCanCreateMonitor(new User(), TENANT_IDS))
        .isInstanceOf(MonitorLimitService.MonitorLimitReachedException.class)
        .hasMessageContaining("maximum of 3 monitors");
  }

  @Test
  void noCountWithoutLimit() {
    User user = User.builder().maxMonitors(0).build();
    monitorLimitService.checkCanCreateMonitor(user, TENANT_IDS);
    verifyNoInteractions(monitorRepository);
  }
}
