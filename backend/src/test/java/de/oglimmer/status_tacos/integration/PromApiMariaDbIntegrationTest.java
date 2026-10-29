/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import de.oglimmer.status_tacos.service.ApiTokenService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
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

/** The Prometheus query API as Grafana uses it: API token, SQL and migration on a real MariaDB. */
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
class PromApiMariaDbIntegrationTest {

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
  @Autowired private MonitorRepository monitorRepository;
  @Autowired private ApiTokenService apiTokenService;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void grafanaReadsTheMonitorsOfTheTokenUser() throws Exception {
    long suffix = System.nanoTime() % 1_000_000;
    Tenant tenant =
        tenantRepository.save(Tenant.builder().name("Prod").code("PR" + suffix).build());
    User user =
        userRepository.save(
            User.builder()
                .email("prom" + suffix + "@example.com")
                .oidcSubject("sub-prom-" + suffix)
                .tenants(new HashSet<>(Set.of(tenant)))
                .build());
    Monitor shop = monitor(tenant, "Shop " + suffix);
    Tenant otherTenant =
        tenantRepository.save(Tenant.builder().name("Other").code("OT" + suffix).build());
    Monitor foreign = monitor(otherTenant, "Foreign " + suffix);

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    check(shop, now.minusMinutes(3), true, 120, 200);
    check(shop, now.minusMinutes(2), true, 80, 200);
    check(shop, now.minusMinutes(1), false, null, 503);
    check(foreign, now.minusMinutes(1), true, 50, 200);
    LocalDateTime lastHour = now.truncatedTo(ChronoUnit.HOURS).minusHours(1);
    jdbc.update(
        "INSERT INTO check_rollup_hourly (monitor_id, bucket_start, total_checks, up_checks,"
            + " total_seconds, up_seconds, rt_count, rt_sum, rt_min, rt_max)"
            + " VALUES (?, ?, 60, 30, 3600, 1800, 30, 3000, 50, 250)",
        shop.getId(),
        lastHour);

    String token = apiTokenService.create(user, "Grafana").token();
    String bearer = "Bearer " + token;
    String time = String.valueOf(now.toEpochSecond(ZoneOffset.UTC));
    String shopSelector = "{monitor_id=\"" + shop.getId() + "\"}";

    // No token or a wrong token: 401
    mockMvc
        .perform(get("/prom/api/v1/query").param("query", "1+1"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/prom/api/v1/query")
                .param("query", "1+1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer stt_wrong"))
        .andExpect(status().isUnauthorized());

    // Grafana's "Save & test": POST form with basic auth
    mockMvc
        .perform(
            post("/prom/api/v1/query")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("query", "1+1")
                .header(HttpHeaders.AUTHORIZATION, basic("grafana", token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.resultType").value("scalar"))
        .andExpect(jsonPath("$.data.result[1]").value("2"));

    mockMvc
        .perform(
            get("/prom/api/v1/query")
                .param("query", "probe_success" + shopSelector)
                .param("time", time)
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultType").value("vector"))
        .andExpect(jsonPath("$.data.result", hasSize(1)))
        .andExpect(jsonPath("$.data.result[0].metric.monitor").value(shop.getName()))
        .andExpect(jsonPath("$.data.result[0].metric.tenant").value("Prod"))
        .andExpect(jsonPath("$.data.result[0].value[1]").value("0"));

    mockMvc
        .perform(
            get("/prom/api/v1/query")
                .param("query", "avg_over_time(probe_duration_seconds" + shopSelector + "[10m])")
                .param("time", time)
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(jsonPath("$.data.result[0].value[1]").value("0.1"));

    mockMvc
        .perform(
            get("/prom/api/v1/query")
                .param("query", "tacos_uptime_ratio_1h" + shopSelector)
                .param("time", time)
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(jsonPath("$.data.result[0].value[1]").value("0.5"));

    mockMvc
        .perform(
            get("/prom/api/v1/query_range")
                .param("query", "probe_http_status_code" + shopSelector)
                .param("start", String.valueOf(now.minusMinutes(3).toEpochSecond(ZoneOffset.UTC)))
                .param("end", time)
                .param("step", "60")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultType").value("matrix"))
        .andExpect(jsonPath("$.data.result[0].values", hasSize(4)))
        .andExpect(jsonPath("$.data.result[0].values[3][1]").value("503"));

    // Only the monitors of the user's tenants
    mockMvc
        .perform(get("/prom/api/v1/label/monitor/values").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(jsonPath("$.data", hasItem(shop.getName())))
        .andExpect(jsonPath("$.data", not(hasItem(foreign.getName()))));
    mockMvc
        .perform(
            get("/prom/api/v1/query")
                .param("query", "probe_success{monitor_id=\"" + foreign.getId() + "\"}")
                .param("time", time)
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(jsonPath("$.data.result", hasSize(0)));

    mockMvc
        .perform(
            get("/prom/api/v1/query")
                .param("query", "rate(probe_success[5m])")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorType").value("bad_data"));

    assertThat(apiTokenService.list(user).getFirst().lastUsedAt()).isNotNull();

    // Deleting the user deletes the token (ON DELETE CASCADE).
    jdbc.update("DELETE FROM user_tenant WHERE user_id = ?", user.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", user.getId());
    assertThat(apiTokenService.authenticate(token)).isEmpty();
  }

  private Monitor monitor(Tenant tenant, String name) {
    return monitorRepository.save(
        Monitor.builder()
            .name(name)
            .url("https://" + name.replace(' ', '-').toLowerCase() + ".example.com")
            .tenant(tenant)
            .tenantId(tenant.getId())
            .state(MonitorState.ACTIVE)
            .checkInterval(60)
            .build());
  }

  private void check(
      Monitor monitor, LocalDateTime at, boolean up, Integer responseTimeMs, int statusCode) {
    jdbc.update(
        "INSERT INTO check_results (monitor_id, tenant_id, checked_at, status_code,"
            + " response_time_ms, is_up, check_interval) VALUES (?, ?, ?, ?, ?, ?, 60)",
        monitor.getId(),
        monitor.getTenantId(),
        at,
        statusCode,
        responseTimeMs,
        up);
  }

  private static String basic(String user, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }
}
