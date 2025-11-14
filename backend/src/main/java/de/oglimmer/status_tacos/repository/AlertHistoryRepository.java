/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.AlertHistory;
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
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

  List<AlertHistory> findByMonitorIdAndTenantIdOrderBySentAtDesc(
      Integer monitorId, Integer tenantId);

  Page<AlertHistory> findByMonitorIdAndTenantIdOrderBySentAtDesc(
      Integer monitorId, Integer tenantId, Pageable pageable);

  List<AlertHistory> findByTenantIdAndAlertTypeOrderBySentAtDesc(
      Integer tenantId, AlertHistory.AlertType alertType);

  Optional<AlertHistory> findTopByMonitorIdAndTenantIdAndAlertTypeOrderBySentAtDesc(
      Integer monitorId, Integer tenantId, AlertHistory.AlertType alertType);

  @Query(
      "SELECT ah FROM AlertHistory ah WHERE ah.monitor.id = :monitorId "
          + "AND ah.tenantId = :tenantId AND ah.alertType = :alertType AND ah.sentAt >= :since")
  List<AlertHistory> findRecentAlertsByTenantId(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("alertType") AlertHistory.AlertType alertType,
      @Param("since") LocalDateTime since);

  @Query(
      "SELECT COUNT(ah) FROM AlertHistory ah WHERE ah.monitor.id = :monitorId "
          + "AND ah.tenantId = :tenantId AND ah.alertType = :alertType AND ah.sentAt >= :since")
  long countRecentAlertsByTenantId(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("alertType") AlertHistory.AlertType alertType,
      @Param("since") LocalDateTime since);

  List<AlertHistory> findByTenantIdAndEmailSentToIgnoreCaseOrderBySentAtDesc(
      Integer tenantId, String email);

  @Query(
      "SELECT ah FROM AlertHistory ah WHERE ah.tenantId = :tenantId "
          + "AND ah.sentAt BETWEEN :start AND :end ORDER BY ah.sentAt DESC")
  List<AlertHistory> findByTenantIdAndSentAtBetween(
      @Param("tenantId") Integer tenantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  void deleteByTenantIdAndSentAtBefore(Integer tenantId, LocalDateTime cutoffDate);

  @Query(
      "SELECT COUNT(ah) FROM AlertHistory ah WHERE ah.tenantId = :tenantId AND ah.sentAt >= :since")
  long countAlertsSinceByTenantId(
      @Param("tenantId") Integer tenantId, @Param("since") LocalDateTime since);
}
