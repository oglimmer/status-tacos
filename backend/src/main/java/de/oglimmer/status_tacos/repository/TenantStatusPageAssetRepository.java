/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.repository;

import de.oglimmer.status_tacos.persistence.TenantStatusPageAsset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantStatusPageAssetRepository
    extends JpaRepository<TenantStatusPageAsset, Long> {

  /** An asset without its data. */
  interface AssetInfo {
    String getName();

    String getContentType();

    Integer getSize();

    String getSha256();

    LocalDateTime getUpdatedAt();
  }

  /** Reads no file data. */
  List<AssetInfo> findByTenantIdOrderByName(Integer tenantId);

  Optional<TenantStatusPageAsset> findByTenantIdAndName(Integer tenantId, String name);

  @Query(
      "select coalesce(sum(a.size), 0) from TenantStatusPageAsset a"
          + " where a.tenantId = :tenantId and a.name <> :name")
  long sumSizeExcept(@Param("tenantId") Integer tenantId, @Param("name") String name);

  long countByTenantIdAndNameNot(Integer tenantId, String name);
}
