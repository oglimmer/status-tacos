/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
