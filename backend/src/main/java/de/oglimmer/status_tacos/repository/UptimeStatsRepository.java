/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.UptimeStats;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UptimeStatsRepository extends JpaRepository<UptimeStats, Long> {

  List<UptimeStats> findByMonitorIdAndTenantIdOrderByPeriodStartDesc(
      Integer monitorId, Integer tenantId);

  @Query(
      "SELECT us FROM UptimeStats us JOIN FETCH us.monitor JOIN FETCH us.tenant "
          + "WHERE us.monitor.id = :monitorId AND us.tenantId = :tenantId "
          + "ORDER BY us.periodType ASC")
  List<UptimeStats> findByMonitorIdAndTenantIdOrderByPeriodTypeAsc(
      @Param("monitorId") Integer monitorId, @Param("tenantId") Integer tenantId);

  @Query(
      "SELECT us FROM UptimeStats us JOIN FETCH us.monitor JOIN FETCH us.tenant "
          + "WHERE us.monitor.id = :monitorId AND us.tenantId = :tenantId AND us.periodType = :periodType "
          + "ORDER BY us.periodStart DESC")
  List<UptimeStats> findByMonitorIdAndTenantIdAndPeriodTypeOrderByPeriodStartDesc(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("periodType") UptimeStats.PeriodType periodType);

  Optional<UptimeStats> findByMonitorIdAndTenantIdAndPeriodTypeAndPeriodStart(
      Integer monitorId,
      Integer tenantId,
      UptimeStats.PeriodType periodType,
      LocalDateTime periodStart);

  @Query(
      "SELECT us FROM UptimeStats us WHERE us.monitor.id = :monitorId "
          + "AND us.tenantId = :tenantId AND us.periodType = :periodType AND us.periodStart >= :since "
          + "ORDER BY us.periodStart DESC")
  List<UptimeStats> findRecentStatsByTenantId(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("periodType") UptimeStats.PeriodType periodType,
      @Param("since") LocalDateTime since);

  @Query(
      "SELECT us FROM UptimeStats us WHERE us.tenantId = :tenantId AND us.periodType = :periodType "
          + "AND us.monitor.state = 'ACTIVE' ORDER BY us.uptimePercentage ASC")
  List<UptimeStats> findByTenantIdAndPeriodTypeOrderByUptimePercentageAsc(
      @Param("tenantId") Integer tenantId, @Param("periodType") UptimeStats.PeriodType periodType);

  @Query(
      "SELECT us FROM UptimeStats us WHERE us.tenantId = :tenantId AND us.periodType = :periodType "
          + "AND us.monitor.state = 'ACTIVE' ORDER BY us.uptimePercentage DESC")
  List<UptimeStats> findByTenantIdAndPeriodTypeOrderByUptimePercentageDesc(
      @Param("tenantId") Integer tenantId, @Param("periodType") UptimeStats.PeriodType periodType);

  @Query(
      "SELECT AVG(us.uptimePercentage) FROM UptimeStats us WHERE us.tenantId = :tenantId "
          + "AND us.periodType = :periodType AND us.monitor.state = 'ACTIVE' AND us.calculatedAt >= :since")
  Double getAverageUptimeByTenantIdAndPeriodType(
      @Param("tenantId") Integer tenantId,
      @Param("periodType") UptimeStats.PeriodType periodType,
      @Param("since") LocalDateTime since);

  @Query(
      "SELECT us FROM UptimeStats us WHERE us.tenantId = :tenantId AND us.monitor.state = 'ACTIVE' "
          + "AND us.periodType = :periodType AND us.uptimePercentage < :threshold")
  List<UptimeStats> findLowUptimeMonitorsByTenantId(
      @Param("tenantId") Integer tenantId,
      @Param("periodType") UptimeStats.PeriodType periodType,
      @Param("threshold") Double threshold);

  void deleteByTenantIdAndCalculatedAtBefore(Integer tenantId, LocalDateTime cutoffDate);

  @Query(
      "SELECT COUNT(us) FROM UptimeStats us WHERE us.tenantId = :tenantId "
          + "AND us.periodType = :periodType AND us.calculatedAt >= :since")
  long countStatsSinceByTenantId(
      @Param("tenantId") Integer tenantId,
      @Param("periodType") UptimeStats.PeriodType periodType,
      @Param("since") LocalDateTime since);
}
