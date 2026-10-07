/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.StatsPeriod;
import de.oglimmer.status_tacos.dto.UptimeStatsResponseDto;
import de.oglimmer.status_tacos.service.UptimeStatsService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/uptime-stats")
@RequiredArgsConstructor
@Slf4j
public class UptimeStatsController {

  private final UptimeStatsService uptimeStatsService;
  private final UserTenantResolver userTenantResolver;

  /** Stats of all monitors of the user, for one period (for example "seven_days"). */
  @GetMapping
  public ResponseEntity<List<UptimeStatsResponseDto>> getUptimeStatsOfAllMonitors(
      @RequestParam("period") String periodType) {
    Optional<StatsPeriod> period = StatsPeriod.fromPath(periodType);
    if (period.isEmpty()) {
      log.warn("Invalid period type: {}", periodType);
      return ResponseEntity.badRequest().build();
    }
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    return ResponseEntity.ok(uptimeStatsService.getStatsOfAllMonitors(tenantIds, period.get()));
  }

  /** Stats of one monitor, one item per period. */
  @GetMapping("/{monitorId}")
  public ResponseEntity<List<UptimeStatsResponseDto>> getUptimeStats(
      @PathVariable Integer monitorId) {
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug("Getting uptime stats for monitor {} and tenants: {}", monitorId, tenantIds);

    List<UptimeStatsResponseDto> stats =
        Arrays.stream(StatsPeriod.values())
            .map(period -> uptimeStatsService.getStats(tenantIds, monitorId, period))
            .flatMap(Optional::stream)
            .toList();
    return stats.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(stats);
  }

  @GetMapping("/{monitorId}/{periodType}")
  public ResponseEntity<UptimeStatsResponseDto> getUptimeStatsByPeriod(
      @PathVariable Integer monitorId, @PathVariable String periodType) {
    Optional<StatsPeriod> period = StatsPeriod.fromPath(periodType);
    if (period.isEmpty()) {
      log.warn("Invalid period type: {}", periodType);
      return ResponseEntity.badRequest().build();
    }
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug(
        "Getting uptime stats for monitor {}, period {} and tenants: {}",
        monitorId,
        period.get(),
        tenantIds);
    return ResponseEntity.of(uptimeStatsService.getStats(tenantIds, monitorId, period.get()));
  }
}
