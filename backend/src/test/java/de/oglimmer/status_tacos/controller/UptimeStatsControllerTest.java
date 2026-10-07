/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.oglimmer.status_tacos.dto.StatsPeriod;
import de.oglimmer.status_tacos.dto.UptimeStatsResponseDto;
import de.oglimmer.status_tacos.service.UptimeStatsService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UptimeStatsControllerTest {

  @Mock private UptimeStatsService uptimeStatsService;
  @Mock private UserTenantResolver userTenantResolver;
  @InjectMocks private UptimeStatsController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void oneMonitor_onePeriod() throws Exception {
    when(userTenantResolver.getCurrentUserTenantIds()).thenReturn(Set.of(1));
    when(uptimeStatsService.getStats(Set.of(1), 5, StatsPeriod.NINETY_DAYS))
        .thenReturn(Optional.of(stats(StatsPeriod.NINETY_DAYS)));

    mockMvc
        .perform(get("/v1/uptime-stats/5/ninety_days"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.periodType").value("NINETY_DAYS"))
        .andExpect(jsonPath("$.uptimePercentage").value(99.99))
        .andExpect(jsonPath("$.responseTimeDataPoints").isArray())
        .andExpect(jsonPath("$.statusDownPeriods").isArray());
  }

  @Test
  void unknownMonitor_isNotFound() throws Exception {
    when(userTenantResolver.getCurrentUserTenantIds()).thenReturn(Set.of(1));
    when(uptimeStatsService.getStats(any(), eq(5), any())).thenReturn(Optional.empty());

    mockMvc.perform(get("/v1/uptime-stats/5/seven_days")).andExpect(status().isNotFound());
    mockMvc.perform(get("/v1/uptime-stats/5")).andExpect(status().isNotFound());
  }

  @Test
  void oneYear_isNoLongerSupported() throws Exception {
    mockMvc
        .perform(get("/v1/uptime-stats/5/three_sixty_five_days"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/v1/uptime-stats").param("period", "three_sixty_five_days"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(uptimeStatsService);
  }

  @Test
  void allMonitors_onePeriod() throws Exception {
    when(userTenantResolver.getCurrentUserTenantIds()).thenReturn(Set.of(1));
    when(uptimeStatsService.getStatsOfAllMonitors(Set.of(1), StatsPeriod.SEVEN_DAYS))
        .thenReturn(List.of(stats(StatsPeriod.SEVEN_DAYS), stats(StatsPeriod.SEVEN_DAYS)));

    mockMvc
        .perform(get("/v1/uptime-stats").param("period", "seven_days"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void oneMonitor_allPeriods() throws Exception {
    when(userTenantResolver.getCurrentUserTenantIds()).thenReturn(Set.of(1));
    when(uptimeStatsService.getStats(eq(Set.of(1)), eq(5), any()))
        .thenAnswer(inv -> Optional.of(stats(inv.getArgument(2))));

    mockMvc
        .perform(get("/v1/uptime-stats/5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].periodType").value("SEVEN_DAYS"))
        .andExpect(jsonPath("$[1].periodType").value("NINETY_DAYS"));
  }

  private static UptimeStatsResponseDto stats(StatsPeriod period) {
    return UptimeStatsResponseDto.builder()
        .monitorId(5)
        .periodType(period)
        .totalChecks(10_000L)
        .successfulChecks(9_999L)
        .uptimePercentage(new BigDecimal("99.99"))
        .responseTimeDataPoints(List.of())
        .statusDownPeriods(List.of())
        .build();
  }
}
