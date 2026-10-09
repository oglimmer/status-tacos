/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

  Optional<User> findByEmail(String email);

  Optional<User> findByOidcSubject(String oidcSubject);

  Optional<User> findByEmailAndIsActiveTrue(String email);

  @Query(
      "SELECT u FROM User u join fetch u.tenants WHERE u.oidcSubject = :oidcSubject AND u.isActive = true")
  Optional<User> findByOidcSubjectAndIsActiveTrue(String oidcSubject);

  //    List<User> findByTenantId(Integer tenantId);
  //
  //    List<User> findByTenantIdAndIsActiveTrue(Integer tenantId);

  boolean existsByEmail(String email);

  boolean existsByOidcSubject(String oidcSubject);

  @Query("SELECT COUNT(u) FROM User u JOIN u.tenants t WHERE t.id = :tenantId")
  long countMembersOfTenant(@Param("tenantId") Integer tenantId);

  /** Bulk delete. The database cascades to tenant memberships, push devices and push contacts. */
  @Modifying
  @Query("DELETE FROM User u WHERE u.id = :id")
  int deleteUserById(@Param("id") Integer id);
}
