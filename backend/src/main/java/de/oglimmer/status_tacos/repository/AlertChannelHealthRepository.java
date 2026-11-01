/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.AlertChannelHealth;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertChannelHealthRepository
    extends JpaRepository<AlertChannelHealth, AlertChannelHealth.Channel> {

  /** Locked: two alert threads (or replicas) must not lose a count. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT h FROM AlertChannelHealth h WHERE h.channel = :channel")
  Optional<AlertChannelHealth> findForUpdate(@Param("channel") AlertChannelHealth.Channel channel);
}
