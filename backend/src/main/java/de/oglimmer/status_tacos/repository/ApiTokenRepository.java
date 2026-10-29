/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.ApiToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

  @Query("SELECT t FROM ApiToken t JOIN FETCH t.user WHERE t.tokenHash = :tokenHash")
  Optional<ApiToken> findByTokenHash(@Param("tokenHash") String tokenHash);

  List<ApiToken> findByUserIdOrderByCreatedAtDesc(Integer userId);

  long countByUserId(Integer userId);

  long deleteByIdAndUserId(Long id, Integer userId);

  @Modifying
  @Query("UPDATE ApiToken t SET t.lastUsedAt = :now WHERE t.id = :id")
  int updateLastUsedAt(@Param("id") Long id, @Param("now") LocalDateTime now);
}
