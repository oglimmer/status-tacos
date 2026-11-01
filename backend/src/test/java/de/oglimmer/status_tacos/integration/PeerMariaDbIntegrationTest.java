/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.oglimmer.status_tacos.config.PeerConfig;
import de.oglimmer.status_tacos.persistence.AlertChannelHealth.Channel;
import de.oglimmer.status_tacos.persistence.ApiTokenScope;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import de.oglimmer.status_tacos.service.AlertDeliveryHealthService;
import de.oglimmer.status_tacos.service.ApiTokenService;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The /peer endpoints with PEER tokens, the migration and the alert channel health on MariaDB. */
@SpringBootTest
@AutoConfigureMockMvc
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
class PeerMariaDbIntegrationTest {

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

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ApiTokenService apiTokenService;
  @Autowired private AlertDeliveryHealthService deliveryHealth;
  @Autowired private PeerConfig peerConfig;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void tearDown() {
    peerConfig.setTenantId(null);
    for (Channel channel : Channel.values()) {
      deliveryHealth.recordSuccess(channel);
    }
  }

  @Test
  void peerTokensAndHeartbeat() throws Exception {
    long suffix = System.nanoTime() % 1_000_000;
    Tenant peerTenant =
        tenantRepository.save(Tenant.builder().name("Infra").code("IN" + suffix).build());
    Tenant otherTenant =
        tenantRepository.save(Tenant.builder().name("Other").code("OT" + suffix).build());
    User admin = user("admin" + suffix, peerTenant);
    User stranger = user("stranger" + suffix, otherTenant);
    peerConfig.setTenantId(peerTenant.getId());

    // Only a member of the peer tenant can create a PEER token
    assertThatThrownBy(() -> apiTokenService.create(stranger, "B", ApiTokenScope.PEER))
        .isInstanceOf(IllegalArgumentException.class);
    String peerToken = apiTokenService.create(admin, "Server B", ApiTokenScope.PEER).token();
    String grafanaToken = apiTokenService.create(admin, "Grafana").token();

    // No token or a GRAFANA token: 401
    mockMvc.perform(get("/peer/heartbeat")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/peer/heartbeat").header(HttpHeaders.AUTHORIZATION, "Bearer " + grafanaToken))
        .andExpect(status().isUnauthorized());
    // A PEER token does not read /prom
    mockMvc
        .perform(
            get("/prom/api/v1/query")
                .param("query", "1+1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + peerToken))
        .andExpect(status().isUnauthorized());

    // The body the peer monitor of the other server matches with "status":"ok"
    mockMvc
        .perform(get("/peer/heartbeat").header(HttpHeaders.AUTHORIZATION, "Bearer " + peerToken))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"ok\"")));

    // The mail server fails 3 times: cannot-alert
    for (int i = 0; i < 3; i++) {
      deliveryHealth.recordFailure(Channel.EMAIL, "smtp.local", "Connection refused");
    }
    mockMvc
        .perform(get("/peer/heartbeat").header(HttpHeaders.AUTHORIZATION, "Bearer " + peerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cannot-alert"))
        .andExpect(jsonPath("$.reason").value("EMAIL: Connection refused"));

    // A check with a bad URL: 400
    mockMvc
        .perform(
            post("/peer/check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + peerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"ftp://x\"}"))
        .andExpect(status().isBadRequest());

    // The user leaves the peer tenant: the token stops working
    jdbc.update(
        "DELETE FROM user_tenant WHERE user_id = ? AND tenant_id = ?",
        admin.getId(),
        peerTenant.getId());
    mockMvc
        .perform(get("/peer/heartbeat").header(HttpHeaders.AUTHORIZATION, "Bearer " + peerToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void migrationCreatesTheChannelRows() {
    assertThat(jdbc.queryForList("SELECT channel FROM alert_channel_health", String.class))
        .containsExactlyInAnyOrder("EMAIL", "PUSH", "WEBHOOK");
  }

  private User user(String name, Tenant tenant) {
    return userRepository.save(
        User.builder()
            .email(name + "@example.com")
            .oidcSubject("sub-" + name)
            .tenants(new HashSet<>(Set.of(tenant)))
            .build());
  }
}
