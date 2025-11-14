/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.oglimmer.status_tacos.dto.ResponseTimeDataPointDto;
import de.oglimmer.status_tacos.dto.StatusDownPeriodsDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.UptimeStats;
import de.oglimmer.status_tacos.repository.UptimeStatsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UptimeStatsService {

  private final UptimeStatsRepository uptimeStatsRepository;
  private final CheckResultService checkResultService;
  private final MonitorService monitorService;
  private final ObjectMapper objectMapper;
  private final UptimeStatsService self;

  public UptimeStatsService(
      UptimeStatsRepository uptimeStatsRepository,
      CheckResultService checkResultService,
      MonitorService monitorService,
      ObjectMapper objectMapper,
      @Lazy UptimeStatsService self) {
    this.uptimeStatsRepository = uptimeStatsRepository;
    this.checkResultService = checkResultService;
    this.monitorService = monitorService;
    this.objectMapper = objectMapper;
    this.self = self;
  }

  public void calculateAndSaveUptimeStats(Integer tenantId) {
    log.info("Starting uptime statistics calculation for all monitors");

    List<Monitor> activeMonitors =
        monitorService.getActiveMonitors(tenantId).stream()
            .map(
                dto ->
                    Monitor.builder()
                        .id(dto.getId())
                        .name(dto.getName())
                        .url(dto.getUrl())
                        .state(dto.getState())
                        .build())
            .toList();

    if (activeMonitors.isEmpty()) {
      log.info("No active monitors found for uptime calculation");
      return;
    }

    LocalDateTime now = LocalDateTime.now();

    for (Monitor monitor : activeMonitors) {
      try {
        self.calculate7DayStats(tenantId, monitor, now);
        self.calculate90DayStats(tenantId, monitor, now);
        self.calculate365DayStats(tenantId, monitor, now);
      } catch (Exception e) {
        log.error(
            "Failed to calculate uptime stats for monitor {}: {}",
            monitor.getId(),
            e.getMessage(),
            e);
      }
    }

    log.info("Completed uptime statistics calculation for {} monitors", activeMonitors.size());
  }

  public void calculate7DayStats(Integer tenantId, Monitor monitor, LocalDateTime now) {
    LocalDateTime start = now.minusDays(7);
    UptimeStats stats =
        calculateStats(tenantId, monitor, UptimeStats.PeriodType.SEVEN_DAYS, start, now);
    if (stats != null) {
      self.saveStats(stats);
    }
  }

  public void calculate90DayStats(Integer tenantId, Monitor monitor, LocalDateTime now) {
    LocalDateTime start = now.minusDays(90);
    UptimeStats stats =
        calculateStats(tenantId, monitor, UptimeStats.PeriodType.NINETY_DAYS, start, now);
    if (stats != null) {
      self.saveStats(stats);
    }
  }

  public void calculate365DayStats(Integer tenantId, Monitor monitor, LocalDateTime now) {
    LocalDateTime start = now.minusDays(365);
    UptimeStats stats =
        calculateStats(tenantId, monitor, UptimeStats.PeriodType.THREE_SIXTY_FIVE_DAYS, start, now);
    if (stats != null) {
      self.saveStats(stats);
    }
  }

  private UptimeStats calculateStats(
      Integer tenantId,
      Monitor monitor,
      UptimeStats.PeriodType periodType,
      LocalDateTime start,
      LocalDateTime end) {

    log.debug(
        "Calculating {} stats for monitor {} from {} to {}",
        periodType,
        monitor.getId(),
        start,
        end);

    long totalChecks = checkResultService.getCheckCount(tenantId, monitor.getId(), start, end);
    if (totalChecks == 0) {
      log.debug("No checks found for monitor {} in period {}", monitor.getId(), periodType);
      return null;
    }

    long successfulChecks =
        checkResultService.getSuccessfulCheckCount(tenantId, monitor.getId(), start, end);
    Double avgResponseTime =
        checkResultService.getAverageResponseTime(tenantId, monitor.getId(), start, end);
    Integer minResponseTime =
        checkResultService.getMinResponseTime(tenantId, monitor.getId(), start, end);
    Integer maxResponseTime =
        checkResultService.getMaxResponseTime(tenantId, monitor.getId(), start, end);
    Integer p99ResponseTime =
        checkResultService.getPercentileResponseTime(tenantId, monitor.getId(), start, end, 99);

    int intervalMinutes = getIntervalMinutes(periodType);
    List<ResponseTimeDataPointDto> responseTimeData =
        checkResultService.getResponseTimeDataPoints(
            tenantId, monitor.getId(), start, end, intervalMinutes);
    List<StatusDownPeriodsDto> statusDownPeriods =
        checkResultService.getStatusDownPeriods(tenantId, monitor.getId(), start, end);

    double uptimePercentage = (double) successfulChecks / totalChecks * 100.0;
    BigDecimal uptimeDecimal =
        BigDecimal.valueOf(uptimePercentage).setScale(2, RoundingMode.HALF_UP);

    LocalDateTime periodStart = getPeriodStart(start, periodType);

    String responseTimeDataJson = null;
    String statusDownPeriodsJson = null;

    try {
      responseTimeDataJson = objectMapper.writeValueAsString(responseTimeData);
      statusDownPeriodsJson = objectMapper.writeValueAsString(statusDownPeriods);
    } catch (Exception e) {
      log.error(
          "Failed to serialize data for monitor {} period {}: {}",
          monitor.getId(),
          periodType,
          e.getMessage());
    }

    // Create or update the stats object, but don't save yet
    UptimeStats existingStats =
        uptimeStatsRepository
            .findByMonitorIdAndTenantIdAndPeriodTypeAndPeriodStart(
                monitor.getId(), tenantId, periodType, periodStart)
            .orElse(null);

    if (existingStats != null) {
      existingStats.setPeriodEnd(end);
      existingStats.setTotalChecks((int) totalChecks);
      existingStats.setSuccessfulChecks((int) successfulChecks);
      existingStats.setUptimePercentage(uptimeDecimal);
      existingStats.setMinResponseTimeMs(minResponseTime);
      existingStats.setMaxResponseTimeMs(maxResponseTime);
      existingStats.setAvgResponseTimeMs(
          avgResponseTime != null ? avgResponseTime.intValue() : null);
      existingStats.setP99ResponseTimeMs(p99ResponseTime);
      existingStats.setResponseTimeData(responseTimeDataJson);
      existingStats.setStatusChangeData(statusDownPeriodsJson);
      existingStats.setCalculatedAt(LocalDateTime.now());

      log.debug(
          "Prepared update for {} stats for monitor {}: {:.2f}% uptime",
          periodType, monitor.getId(), uptimePercentage);
      return existingStats;
    } else {
      UptimeStats newStats =
          UptimeStats.builder()
              .monitor(monitor)
              .tenantId(tenantId)
              .periodType(periodType)
              .periodStart(periodStart)
              .periodEnd(end)
              .totalChecks((int) totalChecks)
              .successfulChecks((int) successfulChecks)
              .uptimePercentage(uptimeDecimal)
              .minResponseTimeMs(minResponseTime)
              .maxResponseTimeMs(maxResponseTime)
              .avgResponseTimeMs(avgResponseTime != null ? avgResponseTime.intValue() : null)
              .p99ResponseTimeMs(p99ResponseTime)
              .responseTimeData(responseTimeDataJson)
              .statusChangeData(statusDownPeriodsJson)
              .build();

      log.debug(
          "Prepared new {} stats for monitor {}: {:.2f}% uptime",
          periodType, monitor.getId(), uptimePercentage);
      return newStats;
    }
  }

  @Transactional
  public void saveStats(UptimeStats stats) {
    uptimeStatsRepository.save(stats);
    log.debug("Saved {} stats for monitor {}", stats.getPeriodType(), stats.getMonitor().getId());
  }

  private LocalDateTime getPeriodStart(LocalDateTime start, UptimeStats.PeriodType periodType) {
    return switch (periodType) {
      case SEVEN_DAYS -> start.withHour(0).withMinute(0).withSecond(0).withNano(0);
      case NINETY_DAYS -> start.withHour(0).withMinute(0).withSecond(0).withNano(0);
      case THREE_SIXTY_FIVE_DAYS -> start.withHour(0).withMinute(0).withSecond(0).withNano(0);
    };
  }

  private int getIntervalMinutes(UptimeStats.PeriodType periodType) {
    return switch (periodType) {
      case SEVEN_DAYS -> 60; // 1 hour intervals for 7 days
      case NINETY_DAYS -> 360; // 6 hour intervals for 90 days
      case THREE_SIXTY_FIVE_DAYS -> 1440; // 1 day intervals for 365 days
    };
  }

  @Transactional
  public void cleanupOldUptimeStats(Integer tenantId, LocalDateTime cutoffDate) {
    log.info("Cleaning up uptime stats older than {}", cutoffDate);
    uptimeStatsRepository.deleteByTenantIdAndCalculatedAtBefore(tenantId, cutoffDate);
    log.info("Cleanup of old uptime stats completed");
  }
}
