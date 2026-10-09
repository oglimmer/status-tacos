/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.MonitorStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MonitorStatusRepository extends JpaRepository<MonitorStatus, Integer> {

  @Query(
      "SELECT ms FROM MonitorStatus ms JOIN FETCH ms.monitor m JOIN FETCH m.tenant WHERE ms.monitorId = :monitorId AND ms.tenantId = :tenantId")
  Optional<MonitorStatus> findByMonitorIdAndTenantId(
      @Param("monitorId") Integer monitorId, @Param("tenantId") Integer tenantId);

  List<MonitorStatus> findByTenantIdAndCurrentStatus(
      Integer tenantId, MonitorStatus.StatusType currentStatus);

  @Query(
      "SELECT ms FROM MonitorStatus ms JOIN FETCH ms.monitor m JOIN FETCH m.tenant WHERE ms.tenantId IN :tenantIds AND m.state IN ('ACTIVE', 'SILENT')")
  List<MonitorStatus> findAllActiveMonitorStatusesByTenantIdIn(
      @Param("tenantIds") Collection<Integer> tenantIds);

  @Query(
      "SELECT ms FROM MonitorStatus ms JOIN FETCH ms.monitor m JOIN FETCH m.tenant WHERE ms.tenantId = :tenantId AND m.state = 'ACTIVE' "
          + "AND ms.currentStatus = :status")
  List<MonitorStatus> findActiveByTenantIdAndCurrentStatus(
      @Param("tenantId") Integer tenantId, @Param("status") MonitorStatus.StatusType status);

  @Query(
      "SELECT COUNT(ms) FROM MonitorStatus ms WHERE ms.tenantId = :tenantId AND ms.monitor.state = 'ACTIVE' "
          + "AND ms.currentStatus = 'up'")
  long countActiveMonitorsUpByTenantId(@Param("tenantId") Integer tenantId);

  @Query(
      "SELECT COUNT(ms) FROM MonitorStatus ms WHERE ms.tenantId = :tenantId AND ms.monitor.state = 'ACTIVE' "
          + "AND ms.currentStatus = 'down'")
  long countActiveMonitorsDownByTenantId(@Param("tenantId") Integer tenantId);

  @Query(
      "SELECT ms FROM MonitorStatus ms JOIN FETCH ms.monitor m JOIN FETCH m.tenant WHERE ms.tenantId = :tenantId AND m.state = 'ACTIVE' "
          + "AND ms.lastResponseTimeMs > :threshold")
  List<MonitorStatus> findSlowResponseMonitorsByTenantId(
      @Param("tenantId") Integer tenantId, @Param("threshold") Integer threshold);

  /** Bulk update. Moves all rows of the monitor to another tenant (monitor move). */
  @Modifying
  @Query("UPDATE MonitorStatus ms SET ms.tenantId = :tenantId WHERE ms.monitorId = :monitorId")
  int updateTenantIdByMonitorId(
      @Param("monitorId") Integer monitorId, @Param("tenantId") Integer tenantId);

  /** Account deletion: removes the rows of a tenant that is deleted. */
  @Modifying
  @Query("DELETE FROM MonitorStatus e WHERE e.tenantId = :tenantId")
  int deleteAllByTenantId(@Param("tenantId") Integer tenantId);
}
