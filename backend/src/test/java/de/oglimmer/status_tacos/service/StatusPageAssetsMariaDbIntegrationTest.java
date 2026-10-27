/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import de.oglimmer.status_tacos.dto.StatusPageAssetDto;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.TenantStatusPageAsset;
import de.oglimmer.status_tacos.repository.TenantRepository;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The Flyway migration of the status page files on a real MariaDB. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
    properties = {
      "spring.datasource.driver-class-name=org.mariadb.jdbc.Driver",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect",
      "spring.jpa.show-sql=false",
      "spring.sql.init.mode=never",
      "spring.jpa.defer-datasource-initialization=false",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/jwks",
      "monitor.scheduling.enabled=false"
    })
class StatusPageAssetsMariaDbIntegrationTest {

  private static MariaDBContainer<?> db;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    if (db == null) {
      db =
          new MariaDBContainer<>("mariadb:11.4")
              .withDatabaseName("status-tacos")
              // Like stage. The config of Testcontainers sets 1M.
              .withCommand("--max-allowed-packet=16M");
      db.start();
    }
    registry.add("spring.datasource.url", db::getJdbcUrl);
    registry.add("spring.datasource.username", db::getUsername);
    registry.add("spring.datasource.password", db::getPassword);
  }

  @Autowired private TenantRepository tenantRepository;
  @Autowired private StatusPageAssetService assetService;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void filesAreStoredReplacedAndDeletedWithTheTenant() {
    long suffix = System.nanoTime() % 1_000_000;
    Tenant tenant =
        tenantRepository.save(Tenant.builder().name("Files").code("files" + suffix).build());
    // Bigger than a BLOB (64 KB), so the column must be a MEDIUMBLOB. Random bytes, like a
    // compressed font or image.
    byte[] font = new byte[StatusPageAssetService.MAX_FILE_BYTES];
    new Random(42).nextBytes(font);

    assetService.save(tenant.getId(), "inter.woff2", font);
    assetService.save(tenant.getId(), "logo.svg", new byte[] {'<', 's'});

    TenantStatusPageAsset stored = assetService.find(tenant.getCode(), "inter.woff2").orElseThrow();
    assertThat(stored.getData()).isEqualTo(font);
    assertThat(stored.getContentType()).isEqualTo("font/woff2");
    assertThat(assetService.list(tenant.getId()))
        .extracting(StatusPageAssetDto::getName, StatusPageAssetDto::getSize)
        .containsExactly(tuple("inter.woff2", font.length), tuple("logo.svg", 2));

    // The same name replaces the file; the limits count the old file not twice.
    assetService.save(tenant.getId(), "inter.woff2", new byte[] {1});
    assertThat(assetService.find(tenant.getCode(), "inter.woff2").orElseThrow().getData())
        .containsExactly(1);

    // A name is unique per tenant.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO tenant_status_page_assets"
                        + " (tenant_id, name, content_type, size, sha256, data)"
                        + " VALUES (?, 'logo.svg', 'image/svg+xml', 1, 'x', x'00')",
                    tenant.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Deleting the tenant deletes its files (ON DELETE CASCADE).
    jdbc.update("DELETE FROM tenant WHERE id = ?", tenant.getId());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_status_page_assets WHERE tenant_id = ?",
                Integer.class,
                tenant.getId()))
        .isZero();
  }
}
