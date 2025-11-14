/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.dto.ResponseTimeDataPointDto;
import de.oglimmer.status_tacos.dto.ResponseTimeHistoryResponseDto;
import de.oglimmer.status_tacos.dto.StatusDownPeriodsDto;
import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CheckResultService {

  private final CheckResultRepository checkResultRepository;

  public CheckResult saveCheckResult(
      Integer tenantId, Monitor monitor, HttpClientService.HttpCheckResult httpResult) {
    log.debug("Saving check result for monitor {}: {}", monitor.getId(), httpResult.getIsUp());

    CheckResult checkResult =
        CheckResult.builder()
            .monitor(monitor)
            .tenantId(tenantId)
            .statusCode(httpResult.getStatusCode())
            .responseTimeMs(httpResult.getResponseTimeMs())
            .isUp(httpResult.getIsUp())
            .errorMessage(httpResult.getErrorMessage())
            .build();

    CheckResult saved = checkResultRepository.save(checkResult);
    log.debug("Check result saved with ID: {}", saved.getId());

    return saved;
  }

  @Transactional(readOnly = true)
  public List<CheckResult> getCheckResultsInTimeRange(
      Integer tenantId, Integer monitorId, LocalDateTime start, LocalDateTime end) {
    log.debug("Getting check results for monitor {} between {} and {}", monitorId, start, end);
    return checkResultRepository.findByMonitorIdAndTenantIdAndCheckedAtBetweenOrderByCheckedAtDesc(
        monitorId, tenantId, start, end);
  }

  @Transactional(readOnly = true)
  public long getCheckCount(
      Integer tenantId, Integer monitorId, LocalDateTime start, LocalDateTime end) {
    log.debug("Getting check count for monitor {} between {} and {}", monitorId, start, end);
    return checkResultRepository.countByMonitorIdAndTenantIdAndCheckedAtBetween(
        monitorId, tenantId, start, end);
  }

  @Transactional(readOnly = true)
  public long getSuccessfulCheckCount(
      Integer tenantId, Integer monitorId, LocalDateTime start, LocalDateTime end) {
    log.debug(
        "Getting successful check count for monitor {} between {} and {}", monitorId, start, end);
    return checkResultRepository.countSuccessfulByMonitorIdAndTenantIdAndCheckedAtBetween(
        monitorId, tenantId, start, end);
  }

  @Transactional(readOnly = true)
  public Double getAverageResponseTime(
      Integer tenantId, Integer monitorId, LocalDateTime start, LocalDateTime end) {
    log.debug(
        "Getting average response time for monitor {} between {} and {}", monitorId, start, end);
    return checkResultRepository.averageResponseTimeByMonitorIdAndTenantIdAndCheckedAtBetween(
        monitorId, tenantId, start, end);
  }

  @Transactional(readOnly = true)
  public Integer getMinResponseTime(
      Integer tenantId, Integer monitorId, LocalDateTime start, LocalDateTime end) {
    log.debug("Getting min response time for monitor {} between {} and {}", monitorId, start, end);
    return checkResultRepository.minResponseTimeByMonitorIdAndTenantIdAndCheckedAtBetween(
        monitorId, tenantId, start, end);
  }

  @Transactional(readOnly = true)
  public Integer getMaxResponseTime(
      Integer tenantId, Integer monitorId, LocalDateTime start, LocalDateTime end) {
    log.debug("Getting max response time for monitor {} between {} and {}", monitorId, start, end);
    return checkResultRepository.maxResponseTimeByMonitorIdAndTenantIdAndCheckedAtBetween(
        monitorId, tenantId, start, end);
  }

  @Transactional(readOnly = true)
  public Integer getPercentileResponseTime(
      Integer tenantId, Integer monitorId, LocalDateTime start, LocalDateTime end, int percentile) {
    log.debug(
        "Getting {}th percentile response time for monitor {} between {} and {}",
        percentile,
        monitorId,
        start,
        end);
    List<Integer> responseTimes =
        checkResultRepository.findResponseTimesByMonitorIdAndTenantIdAndCheckedAtBetween(
            monitorId, tenantId, start, end);

    if (responseTimes.isEmpty()) {
      return null;
    }

    responseTimes.sort(Integer::compareTo);
    int index = (int) Math.ceil((percentile / 100.0) * responseTimes.size()) - 1;
    index = Math.max(0, Math.min(index, responseTimes.size() - 1));

    return responseTimes.get(index);
  }

  @Transactional(readOnly = true)
  public List<ResponseTimeDataPointDto> getResponseTimeDataPoints(
      Integer tenantId,
      Integer monitorId,
      LocalDateTime start,
      LocalDateTime end,
      int intervalMinutes) {
    log.debug(
        "Getting response time data points for monitor {} between {} and {} with {}min intervals",
        monitorId,
        start,
        end,
        intervalMinutes);

    List<CheckResult> checkResults =
        checkResultRepository.findByMonitorIdAndTenantIdAndCheckedAtBetweenOrderByCheckedAtAsc(
            monitorId, tenantId, start, end);

    if (checkResults.isEmpty()) {
      return List.of();
    }

    long totalMinutes = java.time.Duration.between(start, end).toMinutes();
    int totalIntervals = (int) (totalMinutes / intervalMinutes);

    Map<LocalDateTime, Integer> intervalData = new LinkedHashMap<>();

    for (int i = 0; i < totalIntervals; i++) {
      LocalDateTime intervalStart = start.plusMinutes(i * intervalMinutes);
      intervalData.put(intervalStart, null);
    }

    for (CheckResult result : checkResults) {
      LocalDateTime resultTime = result.getCheckedAt();
      long minutesFromStart = java.time.Duration.between(start, resultTime).toMinutes();
      int intervalIndex = (int) (minutesFromStart / intervalMinutes);

      if (intervalIndex >= 0 && intervalIndex < totalIntervals) {
        LocalDateTime intervalStart = start.plusMinutes(intervalIndex * intervalMinutes);
        Integer currentMax = intervalData.get(intervalStart);
        Integer responseTime = result.getResponseTimeMs();

        if (responseTime != null && (currentMax == null || responseTime > currentMax)) {
          intervalData.put(intervalStart, responseTime);
        }
      }
    }

    return intervalData.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .map(
            entry ->
                ResponseTimeDataPointDto.builder()
                    .timestamp(entry.getKey())
                    .maxResponseTimeMs(entry.getValue())
                    .build())
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<StatusDownPeriodsDto> getStatusDownPeriods(
      Integer tenantId, Integer monitorId, LocalDateTime start, LocalDateTime end) {
    log.debug(
        "Getting status down periods for monitor {} between {} and {}", monitorId, start, end);

    List<CheckResult> checkResults =
        checkResultRepository.findByMonitorIdAndTenantIdAndCheckedAtBetweenOrderByCheckedAtAsc(
            monitorId, tenantId, start, end);

    if (checkResults.isEmpty()) {
      return List.of();
    }

    List<StatusDownPeriodsDto> downPeriods = new ArrayList<>();
    LocalDateTime downStartTime = null;

    for (CheckResult result : checkResults) {
      Boolean isUp = result.getIsUp();
      LocalDateTime timestamp = result.getCheckedAt();

      if (!isUp && downStartTime == null) {
        // Start of a down period
        downStartTime = timestamp;
      } else if (isUp && downStartTime != null) {
        // End of a down period
        downPeriods.add(StatusDownPeriodsDto.builder().start(downStartTime).end(timestamp).build());
        downStartTime = null;
      }
    }

    // If we're still in a down period at the end, close it with the end time
    if (downStartTime != null) {
      downPeriods.add(StatusDownPeriodsDto.builder().start(downStartTime).end(end).build());
    }

    return downPeriods;
  }

  public void cleanupOldCheckResults(Integer tenantId, LocalDateTime cutoffDate) {
    log.info("Cleaning up check results older than {}", cutoffDate);
    checkResultRepository.deleteByTenantIdAndCheckedAtBefore(tenantId, cutoffDate);
    log.info("Cleanup of old check results completed");
  }

  @Transactional(readOnly = true)
  public ResponseTimeHistoryResponseDto getResponseTimeHistory24h(
      Integer tenantId, Integer monitorId) {
    log.debug("Getting 24h response time history for monitor: {}", monitorId);

    LocalDateTime end = LocalDateTime.now();
    LocalDateTime start = end.minusHours(24);

    // Get all check results for the last 24 hours
    List<CheckResult> checkResults = getCheckResultsInTimeRange(tenantId, monitorId, start, end);

    if (checkResults.isEmpty()) {
      log.debug("No check results found for monitor {} in the last 24 hours", monitorId);
      return ResponseTimeHistoryResponseDto.builder()
          .monitorId(monitorId)
          .monitorName("Unknown")
          .intervalMinutes(3)
          .totalDataPoints(480)
          .uptimePercentage24h(0.0)
          .totalChecks24h(0)
          .successfulChecks24h(0)
          .dataPoints(List.of())
          .build();
    }

    // Create 480 time slots (3-minute intervals over 24 hours)
    Map<LocalDateTime, Integer> intervalData = new LinkedHashMap<>();

    // Initialize all 480 intervals with null values
    for (int i = 0; i < 480; i++) {
      LocalDateTime intervalStart = start.plusMinutes(i * 3L);
      intervalData.put(intervalStart, null);
    }

    // Group check results by 3-minute intervals and find max response time in each interval
    for (CheckResult result : checkResults) {
      LocalDateTime resultTime = result.getCheckedAt();

      // Find which 3-minute interval this result belongs to
      long minutesFromStart = java.time.Duration.between(start, resultTime).toMinutes();
      int intervalIndex = (int) (minutesFromStart / 3);

      if (intervalIndex >= 0 && intervalIndex < 480) {
        LocalDateTime intervalStart = start.plusMinutes(intervalIndex * 3L);
        Integer currentMax = intervalData.get(intervalStart);
        Integer responseTime = result.getResponseTimeMs();

        if (responseTime != null && (currentMax == null || responseTime > currentMax)) {
          intervalData.put(intervalStart, responseTime);
        }
      }
    }

    // Convert to data points, filtering out null values
    List<ResponseTimeDataPointDto> dataPoints =
        intervalData.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .map(
                entry ->
                    ResponseTimeDataPointDto.builder()
                        .timestamp(entry.getKey())
                        .maxResponseTimeMs(entry.getValue())
                        .build())
            .collect(Collectors.toList());

    // Calculate uptime statistics
    int totalChecks = checkResults.size();
    long successfulChecks =
        checkResults.stream().mapToLong(result -> result.getIsUp() ? 1L : 0L).sum();
    double uptimePercentage =
        totalChecks > 0 ? (double) successfulChecks / totalChecks * 100.0 : 0.0;

    String monitorName =
        checkResults.isEmpty() ? "Unknown" : checkResults.get(0).getMonitor().getName();

    // Generate status down periods
    List<StatusDownPeriodsDto> statusDownPeriods =
        generateStatusDownPeriods(checkResults, start, end);

    log.debug(
        "Uptime calculation for monitor {}: {:.2f}% ({}/{} checks)",
        monitorId, uptimePercentage, successfulChecks, totalChecks);

    return ResponseTimeHistoryResponseDto.builder()
        .monitorId(monitorId)
        .monitorName(monitorName)
        .intervalMinutes(3)
        .totalDataPoints(480)
        .uptimePercentage24h(uptimePercentage)
        .totalChecks24h(totalChecks)
        .successfulChecks24h((int) successfulChecks)
        .dataPoints(dataPoints)
        .statusDownPeriods(statusDownPeriods)
        .build();
  }

  private List<StatusDownPeriodsDto> generateStatusDownPeriods(
      List<CheckResult> checkResults, LocalDateTime start, LocalDateTime end) {
    if (checkResults.isEmpty()) {
      return List.of();
    }

    // Sort by timestamp ascending to process chronologically
    List<CheckResult> sortedResults =
        checkResults.stream()
            .sorted((a, b) -> a.getCheckedAt().compareTo(b.getCheckedAt()))
            .collect(Collectors.toList());

    List<StatusDownPeriodsDto> downPeriods = new ArrayList<>();
    LocalDateTime downStartTime = null;

    for (CheckResult result : sortedResults) {
      Boolean isUp = result.getIsUp();
      LocalDateTime timestamp = result.getCheckedAt();

      if (!isUp && downStartTime == null) {
        // Start of a down period
        downStartTime = timestamp;
      } else if (isUp && downStartTime != null) {
        // End of a down period
        downPeriods.add(StatusDownPeriodsDto.builder().start(downStartTime).end(timestamp).build());
        downStartTime = null;
      }
    }

    // If we're still in a down period at the end, close it with the end time
    if (downStartTime != null) {
      downPeriods.add(StatusDownPeriodsDto.builder().start(downStartTime).end(end).build());
    }

    return downPeriods;
  }
}
