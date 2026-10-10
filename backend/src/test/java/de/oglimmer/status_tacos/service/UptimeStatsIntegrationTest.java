/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.oglimmer.status_tacos.dto.ResponseTimeDataPointDto;
import de.oglimmer.status_tacos.dto.ResponseTimeHistoryResponseDto;
import de.oglimmer.status_tacos.dto.StatsPeriod;
import de.oglimmer.status_tacos.dto.StatusDownPeriodsDto;
import de.oglimmer.status_tacos.dto.UptimeStatsResponseDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.CheckRollupRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the roll-up job and the stats read path on a real MariaDB with the Flyway schema. Every
 * result is compared with a simple calculation over all generated checks (the "oracle").
 */
@SpringBootTest
@ActiveProfiles("test")
// Skipped where no Docker is available, for example in the image build (backend/Dockerfile).
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
      "monitor.scheduling.enabled=false",
      "monitor.cleanup.retention-days=30",
      "logging.level.org.hibernate.SQL=INFO"
    })
class UptimeStatsIntegrationTest {

  // Started when the test context loads, so a skipped run (no Docker) never touches Docker.
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

  /** Not aligned to an hour, so windows and the raw tail have partial buckets. */
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 30, 30);

  private static final LocalDateTime DATA_START = NOW.minusDays(40);

  @TestConfiguration
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired private MutableClock clock;
  @Autowired private CheckRollupService rollupService;
  @Autowired private UptimeStatsService statsService;
  @Autowired private CheckRollupRepository rollupRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private MonitorRepository monitorRepository;
  @Autowired private AlertContactRepository alertContactRepository;
  @Autowired private JdbcTemplate jdbc;

  private Tenant tenant;
  private Monitor monitor;
  private Monitor emptyMonitor;
  private List<Check> checks;

  record Check(LocalDateTime at, boolean up, Integer rt) {}

  @BeforeEach
  void setUp() {
    for (String table :
        List.of(
            "check_results",
            "check_rollup_hourly",
            "check_rollup_hourly_histogram",
            "check_rollup_daily_histogram",
            "monitor_outage",
            "monitor_status",
            "monitors")) {
      jdbc.update("DELETE FROM " + table);
    }
    jdbc.update("UPDATE rollup_state SET done_until = NULL");
    clock.set(NOW);

    tenant =
        tenantRepository.save(
            Tenant.builder()
                .name("Tenant")
                .code("IT" + System.nanoTime() % 1_000_000)
                .isActive(true)
                .build());
    // SILENT monitors are checked, so they must have stats too.
    monitor = saveMonitor("Shop", MonitorState.SILENT);
    emptyMonitor = saveMonitor("New", MonitorState.ACTIVE);
    checks = generateChecks();
    insert(checks);
  }

  @Test
  void noTenants_findNothing() {
    // A user without tenants passes an empty set: the IN queries must run and match nothing.
    assertThat(monitorRepository.findByTenantIdIn(Set.of())).isEmpty();
    assertThat(monitorRepository.findByIdAndTenantIdIn(monitor.getId(), Set.of())).isEmpty();
    assertThat(alertContactRepository.findByTenantIdIn(Set.of())).isEmpty();
    assertThat(statsService.getStatsOfAllMonitors(Set.of(), StatsPeriod.SEVEN_DAYS)).isEmpty();
    assertThat(statsService.getStats(Set.of(), monitor.getId(), StatsPeriod.SEVEN_DAYS)).isEmpty();
  }

  @Test
  void stats_matchRawData_beforeDuringAndAfterRollUp() {
    // Nothing rolled up: everything comes from raw check results.
    assertStatsMatchOracle();

    // Rolled up until 20 days ago (backfill still running): the job is behind the 7-day and 24h
    // window starts, so the state at the window start comes from the last check before it.
    clock.set(NOW.minusDays(20));
    rollupService.rollUp();
    clock.set(NOW);
    assertStatsMatchOracle();

    // Rolled up until 3 days ago: roll-ups plus a 3-day raw tail.
    clock.set(NOW.minusDays(3));
    rollupService.rollUp();
    clock.set(NOW);
    assertThat(rollupRepository.findWatermark(CheckRollupRepository.HOURLY))
        .isEqualTo(NOW.minusDays(3).truncatedTo(ChronoUnit.HOURS));
    assertStatsMatchOracle();

    // Fully rolled up: only the newest hour comes from raw check results.
    rollupService.rollUp();
    assertThat(rollupRepository.findWatermark(CheckRollupRepository.HOURLY))
        .isEqualTo(NOW.truncatedTo(ChronoUnit.HOURS));
    assertThat(rollupRepository.findWatermark(CheckRollupRepository.DAILY))
        .isEqualTo(NOW.truncatedTo(ChronoUnit.DAYS));
    assertStatsMatchOracle();
  }

  @Test
  void stats_stayCorrect_afterRawDataIsDeleted() {
    rollupService.rollUp();
    rollupService.cleanup();

    LocalDateTime oldestRaw =
        jdbc.queryForObject("SELECT MIN(checked_at) FROM check_results", LocalDateTime.class);
    assertThat(oldestRaw).isAfterOrEqualTo(NOW.minusDays(30));
    // The 90-day stats still contain the deleted checks (40 days of data).
    assertStatsMatchOracle();
  }

  @Test
  void cleanup_keepsRawData_thatIsNotRolledUp() {
    rollupService.cleanup();

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM check_results", Long.class))
        .isEqualTo(checks.size());
  }

  @Test
  void rollUp_twoRunsAtTheSameTime_giveTheSameResult() {
    CompletableFuture<Integer> a = CompletableFuture.supplyAsync(rollupService::rollUp);
    CompletableFuture<Integer> b = CompletableFuture.supplyAsync(rollupService::rollUp);

    int hours = a.join() + b.join();

    long expectedHours =
        Duration.between(
                DATA_START.truncatedTo(ChronoUnit.HOURS), NOW.truncatedTo(ChronoUnit.HOURS))
            .toHours();
    assertThat(hours).isEqualTo(expectedHours);
    assertStatsMatchOracle();
  }

  @Test
  void rollUpHour_isIdempotent() {
    rollupService.rollUp();
    String before = snapshot();

    LocalDateTime hour = NOW.minusDays(2).truncatedTo(ChronoUnit.HOURS);
    rollupRepository.rollUpHour(hour);
    rollupRepository.rollUpHour(hour);
    rollupService.rollUp();

    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void stats_ofMonitorWithoutChecks() {
    UptimeStatsResponseDto stats =
        statsService
            .getStats(Set.of(tenant.getId()), emptyMonitor.getId(), StatsPeriod.SEVEN_DAYS)
            .orElseThrow();

    assertThat(stats.getTotalChecks()).isZero();
    assertThat(stats.getUptimePercentage()).isNull();
    assertThat(stats.getP99ResponseTimeMs()).isNull();
    assertThat(stats.getResponseTimeDataPoints()).isEmpty();
    assertThat(stats.getStatusDownPeriods()).isEmpty();
    assertThat(stats.getFirstCheckAt()).isNull();
  }

  @Test
  void stats_ofNewMonitor_startAtItsFirstCheck() {
    rollupService.rollUp();
    Monitor fresh = saveMonitor("Fresh", MonitorState.ACTIVE);
    LocalDateTime first = NOW.minusMinutes(10);
    insert(
        fresh,
        List.of(
            new Check(first, false, null),
            new Check(NOW.minusMinutes(5), false, null),
            new Check(NOW.minusSeconds(15), false, null)));

    UptimeStatsResponseDto stats =
        statsService
            .getStats(Set.of(tenant.getId()), fresh.getId(), StatsPeriod.SEVEN_DAYS)
            .orElseThrow();
    ResponseTimeHistoryResponseDto history =
        statsService.getResponseTimeHistory24h(Set.of(tenant.getId()), fresh.getId()).orElseThrow();

    // Before the first check the monitor did not exist: the downtime bar shows no data there.
    assertThat(stats.getFirstCheckAt()).isEqualTo(first);
    assertThat(history.getFirstCheckAt()).isEqualTo(first);
    assertThat(stats.getStatusDownPeriods())
        .containsExactly(StatusDownPeriodsDto.builder().start(first).end(NOW).build());
  }

  @Test
  void stats_ofAllMonitors_matchSingleMonitorStats() {
    rollupService.rollUp();

    List<UptimeStatsResponseDto> all =
        statsService.getStatsOfAllMonitors(Set.of(tenant.getId()), StatsPeriod.NINETY_DAYS);

    assertThat(all).hasSize(2);
    assertThat(all)
        .filteredOn(s -> s.getMonitorId().equals(monitor.getId()))
        .containsExactly(stats(StatsPeriod.NINETY_DAYS));
  }

  @Test
  void stats_ofOtherTenant_areNotVisible() {
    assertThat(statsService.getStats(Set.of(-1), monitor.getId(), StatsPeriod.SEVEN_DAYS))
        .isEmpty();
    assertThat(statsService.getResponseTimeHistory24h(Set.of(-1), monitor.getId())).isEmpty();
  }

  // --- assertions against the oracle ---

  private void assertStatsMatchOracle() {
    for (StatsPeriod period : StatsPeriod.values()) {
      LocalDateTime start = period.windowStart(NOW);
      UptimeStatsResponseDto stats = stats(period);
      List<Check> window = checksIn(start, NOW);
      List<Integer> rts = responseTimes(window);
      long up = window.stream().filter(Check::up).count();

      assertThat(stats.getPeriodStart()).as(period.name()).isEqualTo(start);
      assertThat(stats.getPeriodEnd()).isEqualTo(NOW);
      assertThat(stats.getTotalChecks()).as(period.name()).isEqualTo(window.size());
      assertThat(stats.getSuccessfulChecks()).as(period.name()).isEqualTo(up);
      assertThat(stats.getUptimePercentage())
          .isEqualTo(
              BigDecimal.valueOf(up * 100)
                  .divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.DOWN));
      assertThat(stats.getMinResponseTimeMs()).isEqualTo(rts.getFirst());
      assertThat(stats.getMaxResponseTimeMs()).isEqualTo(rts.getLast());
      assertThat(stats.getAvgResponseTimeMs())
          .isEqualTo(
              (int)
                  Math.round(
                      rts.stream().mapToLong(Integer::longValue).sum() / (double) rts.size()));
      int exactP99 = rts.get((int) Math.ceil(0.99 * rts.size()) - 1);
      assertThat((double) stats.getP99ResponseTimeMs())
          .as(period.name())
          .isCloseTo(exactP99, within(exactP99 * 0.025));
      assertThat(stats.getResponseTimeDataPoints())
          .as(period.name())
          .isEqualTo(chart(window, start, period.getChartIntervalMinutes()));
      // The test data has 5 outages in the 7-day window and 6 in the 90-day window.
      assertThat(stats.getStatusDownPeriods())
          .as(period.name())
          .hasSize(period == StatsPeriod.SEVEN_DAYS ? 5 : 6)
          .isEqualTo(downPeriods(start, NOW));
      // Exact from raw checks, the start of the hour from the hourly roll-ups.
      LocalDateTime firstCheck = window.getFirst().at();
      assertThat(stats.getFirstCheckAt())
          .as(period.name())
          .isBetween(firstCheck.truncatedTo(ChronoUnit.HOURS), firstCheck);
    }

    ResponseTimeHistoryResponseDto history =
        statsService
            .getResponseTimeHistory24h(Set.of(tenant.getId()), monitor.getId())
            .orElseThrow();
    LocalDateTime start24h = LocalDateTime.of(2026, 10, 6, 12, 33);
    List<Check> window = checksIn(start24h, NOW);
    assertThat(history.getTotalChecks24h()).isEqualTo(window.size());
    assertThat(history.getSuccessfulChecks24h())
        .isEqualTo((int) window.stream().filter(Check::up).count());
    assertThat(history.getDataPoints()).isEqualTo(chart(window, start24h, 3));
    assertThat(history.getStatusDownPeriods()).isEqualTo(downPeriods(start24h, NOW));
    assertThat(history.getFirstCheckAt()).isEqualTo(window.getFirst().at());

    // The dashboard reads all monitors in one request: same result
    assertThat(statsService.getResponseTimeHistory24hOfAllMonitors(Set.of(tenant.getId())))
        .filteredOn(h -> h.getMonitorId().equals(monitor.getId()))
        .containsExactly(history);
  }

  private UptimeStatsResponseDto stats(StatsPeriod period) {
    return statsService.getStats(Set.of(tenant.getId()), monitor.getId(), period).orElseThrow();
  }

  private List<Check> checksIn(LocalDateTime from, LocalDateTime to) {
    return checks.stream().filter(c -> !c.at().isBefore(from) && c.at().isBefore(to)).toList();
  }

  private static List<Integer> responseTimes(List<Check> window) {
    return window.stream().filter(c -> c.up() && c.rt() != null).map(Check::rt).sorted().toList();
  }

  private static List<ResponseTimeDataPointDto> chart(
      List<Check> window, LocalDateTime start, int intervalMinutes) {
    TreeMap<LocalDateTime, Integer> max = new TreeMap<>();
    for (Check c : window) {
      if (c.up() && c.rt() != null) {
        long index = Duration.between(start, c.at()).toMinutes() / intervalMinutes;
        max.merge(start.plusMinutes(index * intervalMinutes), c.rt(), Math::max);
      }
    }
    return max.entrySet().stream()
        .map(
            e ->
                ResponseTimeDataPointDto.builder()
                    .timestamp(e.getKey())
                    .maxResponseTimeMs(e.getValue())
                    .build())
        .toList();
  }

  /** Outages over all checks, clipped to the window. */
  private List<StatusDownPeriodsDto> downPeriods(LocalDateTime from, LocalDateTime to) {
    List<StatusDownPeriodsDto> result = new ArrayList<>();
    LocalDateTime downSince = null;
    for (Check c : checks) {
      if (!c.up() && downSince == null) {
        downSince = c.at();
      } else if (c.up() && downSince != null) {
        addClipped(result, downSince, c.at(), from, to);
        downSince = null;
      }
    }
    if (downSince != null) {
      addClipped(result, downSince, to, from, to);
    }
    return result;
  }

  private static void addClipped(
      List<StatusDownPeriodsDto> result,
      LocalDateTime start,
      LocalDateTime end,
      LocalDateTime from,
      LocalDateTime to) {
    LocalDateTime s = start.isAfter(from) ? start : from;
    LocalDateTime e = end.isBefore(to) ? end : to;
    if (s.isBefore(e)) {
      result.add(StatusDownPeriodsDto.builder().start(s).end(e).build());
    }
  }

  // --- test data ---

  /**
   * One check per minute for 40 days, on full minutes, so checks fall exactly on hour and day
   * edges. Outages: inside an hour, across an hour edge, across midnight, across the 7-day window
   * start, a single DOWN check, one older than the raw retention, and one still open.
   */
  private static List<Check> generateChecks() {
    LocalDateTime windowStart7d = StatsPeriod.SEVEN_DAYS.windowStart(NOW);
    LocalDateTime d3 = NOW.minusDays(3).truncatedTo(ChronoUnit.DAYS);
    LocalDateTime d5 = NOW.minusDays(5).truncatedTo(ChronoUnit.DAYS);
    LocalDateTime d35 = NOW.minusDays(35).truncatedTo(ChronoUnit.DAYS);
    List<LocalDateTime[]> down =
        List.of(
            range(d35.plusHours(10), d35.plusHours(10).plusMinutes(30)),
            range(d3.plusHours(9).plusMinutes(58), d3.plusHours(10).plusMinutes(3)),
            range(d5.minusMinutes(10), d5.plusMinutes(20)),
            range(windowStart7d.minusMinutes(10), windowStart7d.plusMinutes(10)),
            range(d3.plusHours(15), d3.plusHours(15).plusMinutes(1)),
            range(NOW.minusMinutes(3), NOW.plusMinutes(1)));

    List<Check> result = new ArrayList<>();
    int i = 0;
    for (LocalDateTime at = DATA_START.truncatedTo(ChronoUnit.MINUTES);
        at.isBefore(NOW);
        at = at.plusMinutes(1), i++) {
      LocalDateTime t = at;
      // No checks for 2 minutes after the 7-day window start (inside an outage): the state at the
      // window start must come from the last check before it.
      if (!t.isBefore(windowStart7d) && t.isBefore(windowStart7d.plusMinutes(2))) {
        continue;
      }
      boolean up = down.stream().noneMatch(r -> !t.isBefore(r[0]) && t.isBefore(r[1]));
      Integer rt;
      if (i % 997 == 0) {
        rt = null; // a check without response time
      } else if (i % 1000 == 1) {
        rt = 3000 + i % 500; // spikes for the p99
      } else {
        rt = 40 + (i * 37) % 400;
      }
      result.add(new Check(at, up, rt));
    }
    return result;
  }

  private static LocalDateTime[] range(LocalDateTime from, LocalDateTime to) {
    return new LocalDateTime[] {from, to};
  }

  private void insert(List<Check> data) {
    insert(monitor, data);
  }

  private void insert(Monitor target, List<Check> data) {
    jdbc.batchUpdate(
        "INSERT INTO check_results (monitor_id, tenant_id, checked_at, status_code,"
            + " response_time_ms, is_up) VALUES (?, ?, ?, ?, ?, ?)",
        data,
        5000,
        (ps, c) -> {
          ps.setInt(1, target.getId());
          ps.setInt(2, tenant.getId());
          ps.setObject(3, c.at());
          ps.setInt(4, c.up() ? 200 : 500);
          ps.setObject(5, c.rt());
          ps.setBoolean(6, c.up());
        });
  }

  private Monitor saveMonitor(String name, MonitorState state) {
    return monitorRepository.save(
        Monitor.builder()
            .name(name)
            .url("https://" + name.toLowerCase() + ".example.com")
            .tenant(tenant)
            .tenantId(tenant.getId())
            .state(state)
            .build());
  }

  /** All roll-up rows as text, to compare two states of the database. */
  private String snapshot() {
    StringBuilder sb = new StringBuilder();
    for (String sql :
        List.of(
            "SELECT * FROM check_rollup_hourly ORDER BY monitor_id, bucket_start",
            "SELECT * FROM check_rollup_hourly_histogram ORDER BY monitor_id, bucket_start,"
                + " rt_bucket",
            "SELECT * FROM check_rollup_daily_histogram ORDER BY monitor_id, bucket_start,"
                + " rt_bucket",
            "SELECT monitor_id, started_at, ended_at FROM monitor_outage"
                + " ORDER BY monitor_id, started_at",
            "SELECT * FROM rollup_state ORDER BY name")) {
      for (Map<String, Object> row : jdbc.queryForList(sql)) {
        sb.append(row).append('\n');
      }
    }
    return sb.toString();
  }

  /** A clock the test can set. Always UTC. */
  static class MutableClock extends Clock {
    private volatile Instant instant = Instant.now();

    void set(LocalDateTime time) {
      instant = time.toInstant(ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
