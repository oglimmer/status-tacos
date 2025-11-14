/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.CheckResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

  List<CheckResult> findByMonitorIdAndTenantIdOrderByCheckedAtDesc(
      Integer monitorId, Integer tenantId);

  Page<CheckResult> findByMonitorIdAndTenantIdOrderByCheckedAtDesc(
      Integer monitorId, Integer tenantId, Pageable pageable);

  Optional<CheckResult> findTopByMonitorIdAndTenantIdOrderByCheckedAtDesc(
      Integer monitorId, Integer tenantId);

  List<CheckResult> findByMonitorIdAndTenantIdAndCheckedAtBetweenOrderByCheckedAtDesc(
      Integer monitorId, Integer tenantId, LocalDateTime start, LocalDateTime end);

  @Query(
      "SELECT cr FROM CheckResult cr WHERE cr.monitor.id = :monitorId "
          + "AND cr.tenantId = :tenantId AND cr.checkedAt >= :since ORDER BY cr.checkedAt DESC")
  List<CheckResult> findRecentByMonitorIdAndTenantId(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("since") LocalDateTime since);

  @Query(
      "SELECT COUNT(cr) FROM CheckResult cr WHERE cr.monitor.id = :monitorId "
          + "AND cr.tenantId = :tenantId AND cr.checkedAt BETWEEN :start AND :end")
  long countByMonitorIdAndTenantIdAndCheckedAtBetween(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT COUNT(cr) FROM CheckResult cr WHERE cr.monitor.id = :monitorId "
          + "AND cr.tenantId = :tenantId AND cr.isUp = true AND cr.checkedAt BETWEEN :start AND :end")
  long countSuccessfulByMonitorIdAndTenantIdAndCheckedAtBetween(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT AVG(cr.responseTimeMs) FROM CheckResult cr WHERE cr.monitor.id = :monitorId "
          + "AND cr.tenantId = :tenantId AND cr.isUp = true AND cr.checkedAt BETWEEN :start AND :end")
  Double averageResponseTimeByMonitorIdAndTenantIdAndCheckedAtBetween(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT MIN(cr.responseTimeMs) FROM CheckResult cr WHERE cr.monitor.id = :monitorId "
          + "AND cr.tenantId = :tenantId AND cr.isUp = true AND cr.checkedAt BETWEEN :start AND :end")
  Integer minResponseTimeByMonitorIdAndTenantIdAndCheckedAtBetween(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT MAX(cr.responseTimeMs) FROM CheckResult cr WHERE cr.monitor.id = :monitorId "
          + "AND cr.tenantId = :tenantId AND cr.isUp = true AND cr.checkedAt BETWEEN :start AND :end")
  Integer maxResponseTimeByMonitorIdAndTenantIdAndCheckedAtBetween(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      "SELECT cr.responseTimeMs FROM CheckResult cr WHERE cr.monitor.id = :monitorId "
          + "AND cr.tenantId = :tenantId AND cr.isUp = true AND cr.responseTimeMs IS NOT NULL "
          + "AND cr.checkedAt BETWEEN :start AND :end ORDER BY cr.responseTimeMs")
  List<Integer> findResponseTimesByMonitorIdAndTenantIdAndCheckedAtBetween(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  List<CheckResult> findByMonitorIdAndTenantIdAndCheckedAtBetweenOrderByCheckedAtAsc(
      Integer monitorId, Integer tenantId, LocalDateTime start, LocalDateTime end);

  void deleteByTenantIdAndCheckedAtBefore(Integer tenantId, LocalDateTime cutoffDate);

  @Query(
      "SELECT cr FROM CheckResult cr WHERE cr.tenantId = :tenantId AND cr.isUp = false ORDER BY cr.checkedAt DESC")
  Page<CheckResult> findFailedChecksByTenantId(
      @Param("tenantId") Integer tenantId, Pageable pageable);
}
