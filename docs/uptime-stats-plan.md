# Uptime stats: implementation plan

Status: in progress, 2026-10-07. Scope change: the 365-day period is removed. Max period is 90 days.

Fixes every item in
[uptime-stats-review.md](uptime-stats-review.md) (issues #1-#16).

Priorities, in order: data correctness, reliability, performance.

## 0. Implementation status (2026-10-07, not committed)

Done, in one change set (backend + frontend):

- Migration `V0_0_15__add_check_rollups.sql`: hourly roll-up, hourly and
  daily histograms, `monitor_outage`, `rollup_state`, `shedlock`.
- `CheckRollupService` (job + batched cleanup), `CheckRollupRepository`
  (SQL), `OutageTracker`, `ResponseTimeHistogram`.
- `UptimeStatsService` computes stats on request. New endpoint
  `GET /v1/uptime-stats?period=…` for all monitors. 365 days returns 400.
- ShedLock on all scheduled jobs, UTC `Clock`, UTC JVM default.
- Old `UptimeStats` entity, repository, 15-minute job removed.
- Frontend: no 365-day option, one `formatUptime` (rounds down), uptime from
  the backend only, charts use the backend window, one stats request per
  timeframe, stats refresh every 60 s.
- Tests: 86 backend tests (MariaDB via Testcontainers for the roll-up and
  read path), 42 frontend unit tests.

Changes from the plan below:

- No `check_rollup_daily` counts table. The 90-day chart needs the hourly
  rows anyway, so the counts come from them. Only the daily histogram is
  kept (for the 90-day p99).
- All steps shipped together, not as separate PRs. The comparison with the
  old calculation is in `UptimeStatsIntegrationTest` (an independent
  calculation over all generated checks), not a debug endpoint.

Still open:

- `DROP TABLE uptime_stats`: do it in a later release, so a rollback to the
  old version still works. Nothing reads or writes the table now.
- #14 (`TIMESTAMP` columns end in 2038): not done.

## 1. Target design (short)

Replace "recompute everything from raw checks every 15 minutes" with
**incremental roll-ups + compute on read**:

```
check_results (raw, 90 d)
   │  rollup job, once per closed hour, idempotent, one global watermark
   ├─► check_rollup_hourly            (counts + response-time sums,   92 d)
   ├─► check_rollup_hourly_histogram  (response-time histogram,        8 d)
   ├─► monitor_outage                 (down periods, start/end,       92 d)
   │  once per closed UTC day, from hourly rows
   └─► check_rollup_daily_histogram   (response-time histogram,       92 d)

read (API request) = closed roll-up rows + raw rows after the watermark
```

The `uptime_stats` table and the 15-minute stats job go away.

### Design decisions

| # | Decision | Why |
|---|----------|-----|
| D1 | One definition of uptime: `up checks / all checks` in the window, computed only in the backend. | Fixes #10. Check-based works for young monitors and for gaps with no checks. Same as today's backend number. |
| D2 | All windows are half-open `[start, end)`. No `BETWEEN`. | No check is counted in two buckets. |
| D3 | Windows align to UTC hours/days. 7d = `[floorHour(now) - 7d, now)`. 90d = `[floorDay(now) - 90 d, now)`. | Fixes #7. Stable buckets, so stats do not move every 15 min. The window is up to 1 hour/1 day longer than N. Document this in the API. |
| D4 | Read = roll-up rows before the watermark + raw rows after it, read in one read-only transaction (one snapshot). | Exact numbers, no gap, no double count, no lag. Raw part is at most ~2 h per monitor. |
| D5 | Roll-up job is idempotent: it recomputes a whole hour with upsert, and moves the watermark in the same transaction. | A rerun, a crash or two replicas give the same result. |
| D6 | Percentiles come from log-scale histograms (bucket factor 1.05, max error about ±2.5 %). | A histogram can be summed across hours and days, so p99 needs no raw rows. |
| D7 | Min/max/avg/p99 and chart values use **successful checks only**. | Fixes #6. One rule for all response-time numbers. Failed checks are timeouts or errors, not latency. |
| D8 | Uptime is rounded **down** to 2 decimals, in one place. | Fixes #5. Never show 100.00 % with downtime. |
| D9 | Roll-up and outage tables have **no `tenant_id`**. Key = `monitor_id`. Access is checked through the monitor. | A monitor move needs no update of these tables. Less code, fewer bugs. |
| D10 | All new time code uses an injected `Clock` (UTC). New columns are `DATETIME`, written in UTC. | Fixes #13, avoids #14 for new tables, makes tests deterministic. |
| D11 | Scheduled jobs use ShedLock (JDBC lock table). | Fixes #4. Only one replica runs a job at a time. |
| D12 | Roll-ups cover all monitors that have checks (ACTIVE and SILENT). | Fixes #16. |

## 2. Data model

New Flyway migration `V0_0_15__add_check_rollups.sql` (MariaDB):

```sql
CREATE TABLE check_rollup_hourly (
  monitor_id      INT UNSIGNED    NOT NULL,
  bucket_start    DATETIME        NOT NULL,          -- UTC, full hour
  total_checks    INT UNSIGNED    NOT NULL,
  up_checks       INT UNSIGNED    NOT NULL,
  rt_count        INT UNSIGNED    NOT NULL,          -- up checks with a response time
  rt_sum          BIGINT UNSIGNED NOT NULL,
  rt_min          INT UNSIGNED    NULL,
  rt_max          INT UNSIGNED    NULL,
  PRIMARY KEY (monitor_id, bucket_start),
  CONSTRAINT fk_rollup_h_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
);

CREATE TABLE check_rollup_hourly_histogram (
  monitor_id   INT UNSIGNED      NOT NULL,
  bucket_start DATETIME          NOT NULL,
  rt_bucket    SMALLINT UNSIGNED NOT NULL,   -- FLOOR(LN(GREATEST(rt,1)) / LN(1.05))
  cnt          INT UNSIGNED      NOT NULL,
  PRIMARY KEY (monitor_id, bucket_start, rt_bucket),
  CONSTRAINT fk_rollup_hh_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
);

CREATE TABLE check_rollup_daily_histogram (  -- same columns
  ...
);

CREATE TABLE monitor_outage (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  monitor_id  INT UNSIGNED    NOT NULL,
  started_at  DATETIME        NOT NULL,   -- first DOWN check
  ended_at    DATETIME        NULL,       -- first UP check after it; NULL = still open
  open_flag   TINYINT AS (IF(ended_at IS NULL, 1, NULL)) PERSISTENT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_outage_one_open (monitor_id, open_flag),   -- max one open outage
  KEY idx_outage_monitor_time (monitor_id, started_at),
  CONSTRAINT fk_outage_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
);

CREATE TABLE rollup_state (
  name        VARCHAR(32) NOT NULL,       -- 'hourly', 'daily'
  done_until  DATETIME    NULL,           -- watermark, exclusive
  PRIMARY KEY (name)
);
INSERT INTO rollup_state (name, done_until) VALUES ('hourly', NULL), ('daily', NULL);

CREATE TABLE shedlock (
  name       VARCHAR(64)  NOT NULL,
  lock_until TIMESTAMP(3) NOT NULL,
  locked_at  TIMESTAMP(3) NOT NULL,
  locked_by  VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
);
```

Size per monitor (15 s checks): hourly ~2,200 rows, daily ~92 rows,
hourly histogram ~8 d × 24 × ~20 buckets ≈ 4,000 rows, daily histogram
~92 × 20 ≈ 1,800 rows. Compare: today ~518,000 raw rows are scanned
per monitor and period every 15 minutes.

## 3. Roll-up job (`CheckRollupService`)

Runs every 5 minutes with `@SchedulerLock(name = "check-rollup")`.

**Hourly step** (repeat until caught up, max 48 hours per run so a backfill
does not hold the lock for too long):

1. `SELECT done_until FROM rollup_state WHERE name='hourly' FOR UPDATE`.
   If `NULL`, start at `floorHour(MIN(check_results.checked_at))`.
2. Next hour `H`. Stop if `H + 1h + grace(2 min) > now`. The grace covers
   checks that commit a little after their `checked_at`.
3. In **one transaction**:
   - Upsert `check_rollup_hourly` for hour `H`, all monitors, with
     `INSERT ... SELECT ... GROUP BY monitor_id ... ON DUPLICATE KEY UPDATE`.
     Filter: `checked_at >= H AND checked_at < H + 1h` (uses `idx_check_time`).
   - Delete and insert `check_rollup_hourly_histogram` rows for hour `H`.
   - Outages: read `(monitor_id, id, checked_at, is_up)` for hour `H`,
     ordered by `monitor_id, checked_at, id` (projection, not entities).
     Apply `OutageTracker` (below) per monitor, starting from its open outage.
   - `UPDATE rollup_state SET done_until = H + 1h WHERE name='hourly'`.

**Daily step:** for each UTC day `D` with `D + 1d <= hourly.done_until` and
`D >= daily.done_until`: rewrite
`check_rollup_daily_histogram` as sums of the 24 hourly rows, then move the
daily watermark. Same transaction.

**`OutageTracker`** — one small pure class, no database:

```java
// Input: current open outage (or none) + checks in time order.
// Output: outages to close, outages to open.
// DOWN with no open outage  -> open, started_at = check time
// UP with an open outage    -> close, ended_at = check time
```

The read path uses the same class for the raw tail (D4). This also
removes the two copies of down-period code (#12).

**Backfill:** no special code. On first start the watermark is `NULL`, so the
job rolls up all 90 days of raw data, 48 hours per run. With a 5-minute
schedule this takes ~2 hours. Add a log line with progress.

## 4. Read path (`UptimeStatsQueryService`)

`getStats(monitorId, period)`, `@Transactional(readOnly = true)`:

1. Load the monitor, check tenant access (as today).
2. `w = hourly.done_until`, `wd = daily.done_until`.
3. Sum the parts of the window:
   - 90d: daily rows in `[start, wd)`, hourly rows in `[wd, w)`.
   - 7d: hourly rows in `[start, w)`.
   - Raw tail: one aggregate query on `check_results` in `[w, now)`
     (`COUNT`, `SUM`, `MIN`, `MAX`, grouped by histogram bucket). Not entities.
4. Uptime, min, max, avg from the sums. p99 from the summed histogram.
5. Chart points: max response time per aligned bucket (7d: 1 h, 90d: 6 h),
   from the hourly rows.
6. Down periods: `monitor_outage` rows that overlap the window, clipped to
   the window, plus the raw tail through `OutageTracker`.

Expected cost per request: 2-4 index range scans, under ~2,500 rows.

Also add `GET /v1/uptime-stats?period=seven_days`: stats for all monitors the
user can see, in one call. The dashboard then makes 1 request, not N.

## 5. API and frontend

API (`UptimeStatsController`):
- Keep `GET /v1/uptime-stats/{monitorId}/{periodType}`.
- Return typed arrays `responseTimeDataPoints` and `statusDownPeriods`
  instead of JSON strings. Remove `id` and `calculatedAt`. Add
  `dataUntil` (= now) so the UI can show freshness.
- `GET /v1/uptime-stats/{monitorId}` returns 3 items (one per period), not
  ~270 rows.
- Controller calls the service, not the repository.

Frontend:
- `stores/monitors.ts`: drop `JSON.parse`, use the typed arrays; use the new
  batch endpoint on the dashboard.
- Delete `calculateUptimePercentage` in `EnhancedStatusChart.vue`. Show the
  backend value (#10).
- One helper `formatUptime(p)` that rounds down to 2 decimals. Use it in
  `UptimeMetrics.vue`, `EnhancedStatusChart.vue`, `MonitorCard.vue`,
  `EnhancedMonitorCard.vue` (today they use `toFixed` / `Math.round`).

The 24h view (`getResponseTimeHistory24h`) stays on raw data (3-minute
buckets, ~5,800 rows). Change it to: projection query instead of entities,
`OutageTracker` for down periods, successful checks only for the chart (D7),
uptime rounded down in the backend.

## 6. Reliability and housekeeping

- **ShedLock** (`net.javacrumbs.shedlock:shedlock-spring` +
  `shedlock-provider-jdbc-template`) on: roll-up job, cleanup job, and the
  monitor check loop (`lockAtMostFor` shorter than the check interval).
  Today's prod uses 1 replica; this makes `replicaCount: 2` safe.
- **Batched deletes** (#11). Replace derived `deleteBy...` with native
  `DELETE ... WHERE checked_at < ? LIMIT 10000` in a loop, each batch in its
  own transaction. Retention per table, in `application.yml`:

  ```yaml
  monitor:
    retention:
      raw-days: 90
      hourly-days: 92        # must be >= 90 for the 90d chart
      hourly-histogram-days: 8
      daily-days: 92         # must be >= 90
  ```

  Validate at startup (`@Validated` config properties) that the rules hold.
  The cleanup must never delete raw rows after `hourly.done_until`.
- **Clock and time zone** (#13). Add a `Clock` bean (`Clock.systemUTC()`).
  Set `spring.jpa.properties.hibernate.jdbc.time_zone: UTC` and
  `-Duser.timezone=UTC` in `backend/Dockerfile`.
- **Logs** (#8). Use `{}` placeholders; format the number before logging.
- **Metrics.** Add Micrometer gauges/timers: roll-up lag
  (`now - hourly.done_until`), job duration, rows written. Alert when lag
  is more than 30 min.

## 7. Tests

Add Testcontainers MariaDB (`org.testcontainers:mariadb`,
`org.testcontainers:junit-jupiter`) for a new test slice that runs Flyway
and the native SQL (#15). Keep H2 for the existing fast tests.

Unit tests (no DB, fixed `Clock`):
- `OutageTracker`: start, end, outage across hours, outage still open, first
  check is DOWN, alternating checks.
- Histogram: bucket index, p99 from buckets, error stays within 2.5 %.
- Window math: hour/day alignment, half-open edges, DST-free (UTC).
- `formatUptime` / backend rounding: 99.996 → 99.99, 100 → 100.00.

Integration tests (MariaDB container):
- Known check pattern → exact uptime, min/max/avg, outages for 7d/90d.
- A check exactly on an hour edge counts once.
- Job run twice → same rows (idempotent).
- Two job runs at the same time → same result (watermark `FOR UPDATE`).
- Backfill from `NULL` watermark equals steady-state result.
- Read result = old raw-based calculation on the same data (uptime equal,
  p99 within 2.5 %). Use `UptimeTestDataGenerator`.
- Monitor move: stats still visible in the new tenant, not in the old one.
- Cleanup keeps rows after the watermark; batches finish.
- SILENT monitor has stats.

Frontend: unit tests for `formatUptime` and the store mapping.

## 8. Work order (one PR each)

| PR | Content | Fixes | Risk |
|----|---------|-------|------|
| 1 | ShedLock + `shedlock` table, `Clock` bean, UTC settings, log placeholders, batched deletes. | #4, #8, #11, #13 | Low |
| 2 | Testcontainers MariaDB test slice. | #15 | Low |
| 3 | Roll-up tables, `CheckRollupService`, `OutageTracker`, histogram, metrics. Runs and backfills; nothing reads it yet. | #2, #12, #16 (prep) | Medium |
| 4 | `UptimeStatsQueryService` + comparison test against old calculation. Hidden debug endpoint that returns old and new side by side. | #1, #3, #5, #6, #7 | Medium |
| 5 | Switch API to the new service, typed arrays, batch endpoint. Frontend: typed arrays, `formatUptime`, remove frontend uptime calc, batch fetch. Ship together. | #3, #5, #9, #10 | Medium |
| 6 | Remove old job, `UptimeStats` entity, repository, `Monitor.uptimeStats`, unused queries, 24h view cleanup. | #3, #9, #12 | Low |
| 7 | Migration: `DROP TABLE uptime_stats` (one release after PR 6, so a rollback is possible). | — | Low |
| 8 | Later: convert `TIMESTAMP` columns to `DATETIME` (big `ALTER` on `check_results`, do in a maintenance window). | #14 | Medium |

#1 (365d covers only 90 days) is fixed by removing the 365-day period from
backend and frontend. The API returns 400 for `three_sixty_five_days`.

## 9. Rollout and checks

1. Deploy PR 3. Watch the backfill log and the lag metric until lag < 10 min.
2. Deploy PR 4. Compare old vs new on prod with the debug endpoint for all
   monitors. Uptime must match (allow ±0.01 for the old HALF_UP rounding).
   p99 within 2.5 %.
3. Deploy PR 5. Check the dashboard: same numbers in card, chart and modal.
4. Check DB load before/after (slow query log, job duration). Expected: the
   15-minute CPU/IO spike goes away.
5. Deploy PR 6, then PR 7 one release later.

## 10. Open questions

- None. Decided: max period 90 days; p99 from histograms (±2.5 %).
