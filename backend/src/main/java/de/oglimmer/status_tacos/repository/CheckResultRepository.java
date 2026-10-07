/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.CheckResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  @Query(
      "SELECT cr FROM CheckResult cr WHERE cr.monitor.id = :monitorId "
          + "AND cr.tenantId = :tenantId AND cr.checkedAt >= :since ORDER BY cr.checkedAt DESC")
  List<CheckResult> findRecentByMonitorIdAndTenantId(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("since") LocalDateTime since);

  /** Checks of one monitor in [from, to), oldest first. */
  @Query(
      "SELECT new de.oglimmer.status_tacos.repository.CheckPoint(cr.checkedAt, cr.isUp,"
          + " cr.responseTimeMs) FROM CheckResult cr WHERE cr.monitor.id = :monitorId"
          + " AND cr.checkedAt >= :from AND cr.checkedAt < :to ORDER BY cr.checkedAt, cr.id")
  List<CheckPoint> findCheckPoints(
      @Param("monitorId") Integer monitorId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      "SELECT cr FROM CheckResult cr WHERE cr.tenantId = :tenantId AND cr.isUp = false ORDER BY cr.checkedAt DESC")
  Page<CheckResult> findFailedChecksByTenantId(
      @Param("tenantId") Integer tenantId, Pageable pageable);

  // Outage start lookups. checked_at has whole seconds only, so the id breaks ties and keeps
  // the check that is being evaluated out. Ordering by checked_at uses idx_check_monitor_time.
  @Query(
      "SELECT c FROM CheckResult c WHERE c.monitor.id = :monitorId AND c.tenantId = :tenantId"
          + " AND c.isUp = true AND c.checkedAt <= :checkedAt AND c.id < :id"
          + " ORDER BY c.checkedAt DESC, c.id DESC")
  List<CheckResult> findLastUpBefore(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("checkedAt") LocalDateTime checkedAt,
      @Param("id") Long id,
      Limit limit);

  @Query(
      "SELECT c FROM CheckResult c WHERE c.monitor.id = :monitorId AND c.tenantId = :tenantId"
          + " AND c.isUp = false AND c.checkedAt >= :checkedAt AND c.id > :id"
          + " ORDER BY c.checkedAt ASC, c.id ASC")
  List<CheckResult> findFirstDownAfter(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("checkedAt") LocalDateTime checkedAt,
      @Param("id") Long id,
      Limit limit);

  /** Bulk update for a monitor move. Moves the rows of the monitor in one checked_at range. */
  @Modifying
  @Query(
      "UPDATE CheckResult cr SET cr.tenantId = :tenantId WHERE cr.monitor.id = :monitorId"
          + " AND cr.checkedAt >= :from AND cr.checkedAt < :to")
  int updateTenantIdByMonitorIdInRange(
      @Param("monitorId") Integer monitorId,
      @Param("tenantId") Integer tenantId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("SELECT MIN(cr.checkedAt) FROM CheckResult cr WHERE cr.monitor.id = :monitorId")
  Optional<LocalDateTime> findOldestCheckedAt(@Param("monitorId") Integer monitorId);
}
