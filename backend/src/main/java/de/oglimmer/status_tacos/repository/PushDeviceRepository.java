/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.PushDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

  Optional<PushDevice> findByToken(String token);

  List<PushDevice> findByUserId(Integer userId);

  long deleteByTokenAndUserId(String token, Integer userId);
}
