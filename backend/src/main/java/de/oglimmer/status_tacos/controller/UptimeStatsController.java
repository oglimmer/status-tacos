/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.dto.UptimeStatsResponseDto;
import de.oglimmer.status_tacos.mapper.EntityMapper;
import de.oglimmer.status_tacos.persistence.UptimeStats;
import de.oglimmer.status_tacos.repository.UptimeStatsRepository;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/uptime-stats")
@RequiredArgsConstructor
@Slf4j
public class UptimeStatsController {

  private final UptimeStatsRepository uptimeStatsRepository;
  private final UserTenantResolver userTenantResolver;
  private final EntityMapper entityMapper;

  @GetMapping("/{monitorId}")
  public ResponseEntity<List<UptimeStatsResponseDto>> getUptimeStats(
      @PathVariable Integer monitorId) {
    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug("Getting uptime stats for monitor {} and tenants: {}", monitorId, tenantIds);

    List<UptimeStats> stats =
        tenantIds.stream()
            .flatMap(
                tenantId ->
                    uptimeStatsRepository
                        .findByMonitorIdAndTenantIdOrderByPeriodTypeAsc(monitorId, tenantId)
                        .stream())
            .toList();

    List<UptimeStatsResponseDto> dtos =
        stats.stream().map(this::convertToDto).collect(Collectors.toList());

    return ResponseEntity.ok(dtos);
  }

  @GetMapping("/{monitorId}/{periodType}")
  public ResponseEntity<UptimeStatsResponseDto> getUptimeStatsByPeriod(
      @PathVariable Integer monitorId, @PathVariable String periodType) {

    Set<Integer> tenantIds = userTenantResolver.getCurrentUserTenantIds();
    log.debug(
        "Getting uptime stats for monitor {}, period {} and tenants: {}",
        monitorId,
        periodType,
        tenantIds);

    UptimeStats.PeriodType period;
    try {
      period = UptimeStats.PeriodType.valueOf(periodType.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid period type: {}", periodType);
      return ResponseEntity.badRequest().build();
    }

    for (Integer tenantId : tenantIds) {
      List<UptimeStats> stats =
          uptimeStatsRepository.findByMonitorIdAndTenantIdAndPeriodTypeOrderByPeriodStartDesc(
              monitorId, tenantId, period);
      if (!stats.isEmpty()) {
        UptimeStatsResponseDto dto = convertToDto(stats.get(0)); // Get most recent
        return ResponseEntity.ok(dto);
      }
    }

    return ResponseEntity.notFound().build();
  }

  private UptimeStatsResponseDto convertToDto(UptimeStats stats) {
    return UptimeStatsResponseDto.builder()
        .id(stats.getId())
        .monitorId(stats.getMonitor().getId())
        .monitorName(stats.getMonitor().getName())
        .tenantId(stats.getTenantId())
        .tenant(entityMapper.toDto(stats.getTenant()))
        .periodType(stats.getPeriodType())
        .periodStart(stats.getPeriodStart())
        .periodEnd(stats.getPeriodEnd())
        .totalChecks(stats.getTotalChecks())
        .successfulChecks(stats.getSuccessfulChecks())
        .uptimePercentage(stats.getUptimePercentage())
        .minResponseTimeMs(stats.getMinResponseTimeMs())
        .maxResponseTimeMs(stats.getMaxResponseTimeMs())
        .avgResponseTimeMs(stats.getAvgResponseTimeMs())
        .p99ResponseTimeMs(stats.getP99ResponseTimeMs())
        .responseTimeData(stats.getResponseTimeData())
        .statusChangeData(stats.getStatusChangeData())
        .calculatedAt(stats.getCalculatedAt())
        .build();
  }
}
