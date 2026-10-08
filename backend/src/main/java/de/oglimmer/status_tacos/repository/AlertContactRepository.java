/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.AlertContact;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertContactRepository extends JpaRepository<AlertContact, Integer> {

  List<AlertContact> findByTenantIdIn(Set<Integer> tenantIds);

  List<AlertContact> findByTenantIdInAndIsActiveTrue(Set<Integer> tenantIds);

  List<AlertContact> findByTenantId(Integer tenantId);

  List<AlertContact> findByTenantIdAndIsActiveTrue(Integer tenantId);

  /** Active contacts of the tenant that are alerted for every monitor or for this monitor. */
  @Query(
      "SELECT DISTINCT ac FROM AlertContact ac LEFT JOIN ac.monitors m"
          + " WHERE ac.tenantId = :tenantId AND ac.isActive = true"
          + " AND (ac.allMonitors = true OR m.id = :monitorId)")
  List<AlertContact> findActiveByTenantIdForMonitor(
      @Param("tenantId") Integer tenantId, @Param("monitorId") Integer monitorId);

  Optional<AlertContact> findByIdAndTenantIdIn(Integer id, Set<Integer> tenantIds);

  @Modifying
  @Query(
      "UPDATE AlertContact ac SET ac.isActive = :isActive WHERE ac.id = :id AND ac.tenantId IN :tenantIds")
  int updateActiveStatus(
      @Param("id") Integer id,
      @Param("isActive") boolean isActive,
      @Param("tenantIds") Set<Integer> tenantIds);

  boolean existsByTenantIdAndValueAndTypeAndIdNot(
      Integer tenantId, String value, AlertContact.AlertContactType type, Integer id);

  boolean existsByTenantIdAndValueAndType(
      Integer tenantId, String value, AlertContact.AlertContactType type);

  Optional<AlertContact> findByTenantIdAndTypeAndOwnerId(
      Integer tenantId, AlertContact.AlertContactType type, Integer ownerId);

  List<AlertContact> findByTypeAndOwnerIdAndTenantIdIn(
      AlertContact.AlertContactType type, Integer ownerId, Set<Integer> tenantIds);

  /** Contacts that are alerted only for selected monitors, and this monitor is one of them. */
  @Query(
      "SELECT ac FROM AlertContact ac JOIN ac.monitors m"
          + " WHERE ac.allMonitors = false AND m.id = :monitorId")
  List<AlertContact> findScopedToMonitor(@Param("monitorId") Integer monitorId);
}
