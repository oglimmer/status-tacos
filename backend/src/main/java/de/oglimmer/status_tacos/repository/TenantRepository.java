/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.Tenant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Integer> {

  Optional<Tenant> findByCode(String code);

  @Query("SELECT t FROM Tenant t WHERE t.isActive = true")
  List<Tenant> findAllActive();

  @Query("SELECT t FROM Tenant t WHERE t.isActive = true AND t.code = :code")
  Optional<Tenant> findActiveByCode(@Param("code") String code);

  boolean existsByCode(String code);
}
