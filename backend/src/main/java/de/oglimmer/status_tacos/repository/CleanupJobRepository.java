/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.CleanupJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CleanupJobRepository extends JpaRepository<CleanupJob, Integer> {

  Optional<CleanupJob> findByTenantIdAndJobType(Integer tenantId, String jobType);

  List<CleanupJob> findByTenantIdAndStatus(Integer tenantId, CleanupJob.JobStatus status);

  @Query(
      "SELECT cj FROM CleanupJob cj WHERE cj.tenantId = :tenantId AND cj.lastRunAt < :cutoffTime")
  List<CleanupJob> findJobsLastRunBeforeByTenantId(
      @Param("tenantId") Integer tenantId, @Param("cutoffTime") LocalDateTime cutoffTime);

  @Query("SELECT cj FROM CleanupJob cj WHERE cj.tenantId = :tenantId ORDER BY cj.lastRunAt ASC")
  List<CleanupJob> findAllByTenantIdOrderByLastRunAtAsc(@Param("tenantId") Integer tenantId);

  @Query(
      "SELECT cj FROM CleanupJob cj WHERE cj.tenantId = :tenantId AND cj.status = 'running' "
          + "AND cj.lastRunAt < :staleThreshold")
  List<CleanupJob> findStaleRunningJobsByTenantId(
      @Param("tenantId") Integer tenantId, @Param("staleThreshold") LocalDateTime staleThreshold);

  @Query(
      "SELECT SUM(cj.recordsDeleted) FROM CleanupJob cj WHERE cj.tenantId = :tenantId AND cj.jobType = :jobType")
  Long getTotalRecordsDeletedByTenantIdAndJobType(
      @Param("tenantId") Integer tenantId, @Param("jobType") String jobType);

  @Query(
      "SELECT AVG(cj.executionTimeMs) FROM CleanupJob cj WHERE cj.tenantId = :tenantId "
          + "AND cj.jobType = :jobType AND cj.executionTimeMs IS NOT NULL")
  Double getAverageExecutionTimeByTenantIdAndJobType(
      @Param("tenantId") Integer tenantId, @Param("jobType") String jobType);
}
