/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.PushDevice;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.PushDeviceRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
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

/** The Flyway migration of the iOS push alerts on a real MariaDB. */
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
class PushAlertsMariaDbIntegrationTest {

  private static MariaDBContainer<?> db;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    if (db == null) {
      db = new MariaDBContainer<>("mariadb:11.4").withDatabaseName("status-tacos");
      db.start();
    }
    registry.add("spring.datasource.url", db::getJdbcUrl);
    registry.add("spring.datasource.username", db::getUsername);
    registry.add("spring.datasource.password", db::getPassword);
  }

  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private MonitorRepository monitorRepository;
  @Autowired private AlertContactRepository alertContactRepository;
  @Autowired private PushDeviceRepository pushDeviceRepository;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void pushAlertsAndDevicesAreStoredAndDeletedWithTheUser() {
    long suffix = System.nanoTime() % 1_000_000;
    Tenant tenant =
        tenantRepository.save(Tenant.builder().name("Prod").code("PU" + suffix).build());
    User owner =
        userRepository.save(
            User.builder()
                .email("push" + suffix + "@example.com")
                .oidcSubject("sub-push-" + suffix)
                .tenants(new HashSet<>(Set.of(tenant)))
                .build());
    Monitor shop =
        monitorRepository.save(
            Monitor.builder()
                .name("Shop")
                .url("https://shop.example.com")
                .tenant(tenant)
                .tenantId(tenant.getId())
                .state(MonitorState.ACTIVE)
                .build());

    AlertContact contact =
        alertContactRepository.save(
            AlertContact.builder()
                .tenant(tenant)
                .tenantId(tenant.getId())
                .type(AlertContact.AlertContactType.IOS_PUSH)
                .value(AlertContact.iosPushValue(owner))
                .name("iOS push")
                .owner(owner)
                .build());
    String token = String.format("%064x", suffix);
    pushDeviceRepository.save(
        PushDevice.builder()
            .user(owner)
            .token(token)
            .environment(PushDevice.Environment.PRODUCTION)
            .lastSeenAt(LocalDateTime.now())
            .build());

    // The alert path finds the contact with the other contact types.
    assertThat(alertContactRepository.findActiveByTenantIdForMonitor(tenant.getId(), shop.getId()))
        .extracting(AlertContact::getId)
        .contains(contact.getId());
    assertThat(
            alertContactRepository.findByTenantIdAndTypeAndOwnerId(
                tenant.getId(), AlertContact.AlertContactType.IOS_PUSH, owner.getId()))
        .isPresent();
    assertThat(pushDeviceRepository.findByUserId(owner.getId())).hasSize(1);

    // A token is unique.
    assertThatThrownBy(
            () ->
                pushDeviceRepository.saveAndFlush(
                    PushDevice.builder()
                        .user(owner)
                        .token(token)
                        .environment(PushDevice.Environment.SANDBOX)
                        .lastSeenAt(LocalDateTime.now())
                        .build()))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Deleting the user deletes the devices and the push alerts (ON DELETE CASCADE).
    jdbc.update("DELETE FROM user_tenant WHERE user_id = ?", owner.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
    assertThat(pushDeviceRepository.findByToken(token)).isEmpty();
    assertThat(alertContactRepository.findById(contact.getId())).isEmpty();
  }
}
