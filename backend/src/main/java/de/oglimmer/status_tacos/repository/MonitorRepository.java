/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MonitorRepository extends JpaRepository<Monitor, Integer> {

  @Query(
      "SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.tenantId = :tenantId AND m.state = :state")
  List<Monitor> findByTenantIdAndState(
      @Param("tenantId") Integer tenantId, @Param("state") MonitorState state);

  // Legacy method for backward compatibility
  @Query(
      "SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.tenantId = :tenantId AND m.state = 'ACTIVE'")
  List<Monitor> findByTenantIdAndIsActiveTrue(@Param("tenantId") Integer tenantId);

  @Query("SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.tenantId = :tenantId")
  List<Monitor> findByTenantId(@Param("tenantId") Integer tenantId);

  @Query("SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.id = :id AND m.tenantId = :tenantId")
  Optional<Monitor> findByIdAndTenantId(
      @Param("id") Integer id, @Param("tenantId") Integer tenantId);

  Optional<Monitor> findByIdAndTenantIdAndState(Integer id, Integer tenantId, MonitorState state);

  @Query(
      "SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.tenantId = :tenantId AND LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%'))")
  List<Monitor> findByTenantIdAndNameContainingIgnoreCase(
      @Param("tenantId") Integer tenantId, @Param("name") String name);

  @Query("SELECT m FROM Monitor m WHERE m.tenantId = :tenantId AND LOWER(m.url) = LOWER(:url)")
  Optional<Monitor> findByTenantIdAndUrlIgnoreCase(
      @Param("tenantId") Integer tenantId, @Param("url") String url);

  long countByTenantIdAndState(Integer tenantId, MonitorState state);

  // Legacy methods for backward compatibility
  @Query("SELECT COUNT(m) FROM Monitor m WHERE m.tenantId = :tenantId AND m.state = 'ACTIVE'")
  long countByTenantIdAndIsActiveTrue(@Param("tenantId") Integer tenantId);

  @Query("SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.tenantId IN :tenantIds")
  List<Monitor> findByTenantIdIn(@Param("tenantIds") Set<Integer> tenantIds);

  @Query(
      "SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.tenantId IN :tenantIds AND m.state = :state")
  List<Monitor> findByTenantIdInAndState(
      @Param("tenantIds") Set<Integer> tenantIds, @Param("state") MonitorState state);

  // Legacy method for backward compatibility
  @Query(
      "SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.tenantId IN :tenantIds AND m.state = 'ACTIVE'")
  List<Monitor> findByTenantIdInAndIsActiveTrue(@Param("tenantIds") Set<Integer> tenantIds);

  @Query(
      "SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.tenantId IN :tenantIds AND LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%'))")
  List<Monitor> findByTenantIdInAndNameContainingIgnoreCase(
      @Param("tenantIds") Set<Integer> tenantIds, @Param("name") String name);

  @Query(
      "SELECT m FROM Monitor m JOIN FETCH m.tenant WHERE m.name = :name AND m.tenantId = :tenantId")
  Optional<Monitor> findByNameAndTenantId(
      @Param("name") String name, @Param("tenantId") Integer tenantId);

  @Query(
      "SELECT m FROM Monitor m JOIN FETCH m.tenant LEFT JOIN FETCH m.monitorStatus WHERE m.tenantId IN :tenantIds")
  List<Monitor> findByTenantIdInWithStatusAndTenant(@Param("tenantIds") Set<Integer> tenantIds);
}
