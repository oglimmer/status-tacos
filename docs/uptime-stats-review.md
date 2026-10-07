# Uptime stats roll-up: review findings

Review date: 2026-10-07. Status: all items fixed (not committed yet), except #14.
See [uptime-stats-plan.md](uptime-stats-plan.md), section 0.

Scope: the job that rolls check results up into `uptime_stats`
(`UptimeStatsService`, `CheckResultService`, `UptimeStatsRepository`,
`UptimeStatsController`, `ScheduledMonitorService`).

How it works today: every 15 minutes (`uptime-stats-cron`) the job computes
7d, 90d and 365d stats for each active monitor straight from raw
`check_results` and saves one `uptime_stats` row per monitor and period.

## Big issues

### 1. 365-day stats only cover 90 days

- The cleanup job deletes `check_results` older than 90 days
  (`monitor.cleanup.retention-days: 90`, `backend/src/main/resources/application.yml:105`).
- The 365d calculation reads the same raw table
  (`UptimeStatsService.java:99`).
- Result: "365d" uptime equals "90d" uptime, and the 365d chart only has
  90 days of points.

### 2. The roll-up job is very heavy

- Each monitor is checked every 15 s: about 5,760 rows/day, about 518,000 rows in 90 days.
- Per monitor and per period, every 15 minutes, the job:
  - loads all `CheckResult` entities for the chart (`CheckResultService.java:137`),
  - loads them all again for the down periods (`CheckResultService.java:188`),
  - loads all response times for p99 (`CheckResultService.java:108`), the query already sorts, and
    the code sorts again at line 116,
  - runs 5 more aggregate queries (count, success count, avg, min, max).
- About 2 million rows loaded per monitor every 15 minutes. This does not scale
  with the number of monitors.

### 3. One new `uptime_stats` row per monitor and period every day

- The row key `periodStart` is "now minus N days, at midnight"
  (`UptimeStatsService.java:150`, `:230`). It changes every day, so each day
  inserts a new row instead of updating the old one.
- Old rows stay until the 90-day cleanup: up to 270 rows per monitor, each
  with LONGTEXT JSON.
- `UptimeStatsController.java:69` loads all rows of one period (JOIN FETCH,
  with JSON) and only uses `stats.get(0)`.
- `UptimeStatsController.java:38` returns all ~270 rows.
- `UptimeStatsRepository.java:52-78` (average uptime, best/worst uptime, low
  uptime monitors) count every daily row, so each monitor is counted many
  times. No code calls these yet. They will give wrong numbers when used.

### 4. Scheduled jobs run on every backend replica

- `helm/values.yaml:23` sets `backend.replicaCount: 2`. There is no
  distributed lock (for example ShedLock).
- The stats job runs once per replica. On the first run of each day, both
  replicas insert the same new row and one fails on `uk_monitor_period`.
- The monitor checks also run once per replica, so check rows are doubled.
- Current deployment uses `helm/custom-values.yaml` with 1 replica, so it is
  not affected today.

## Small issues

5. **Uptime rounds up.** `RoundingMode.HALF_UP` to 2 decimals shows 99.996% as
   "100.00%" even with downtime (`UptimeStatsService.java:148`). Status pages
   usually round down.
6. **Chart and numbers use different checks.** The chart buckets take the max
   response time of all checks, failed ones too
   (`CheckResultService.java:155-169`). Min, max, avg and p99 only use
   successful checks.
7. **Chart buckets drift.** Buckets start at "now minus N days", not on a
   whole hour/day (`CheckResultService.java:150-153`). They shift every 15
   minutes, so the chart moves a little on each run.
8. **Broken log placeholders.** `{:.2f}` is not an SLF4J placeholder, so the
   uptime value is not logged (`UptimeStatsService.java:188`, `:211`; also
   `CheckResultService.java:307`).
9. **Unneeded copy.** `UptimeStatsService.java:45-55` rebuilds each `Monitor`
   into a new `Monitor`. `getActiveMonitors` already returns `Monitor`.

## Found while planning

10. **Frontend computes its own, different uptime.**
    `frontend/src/components/EnhancedStatusChart.vue:288` computes a
    time-based uptime from the down periods over a fixed window (for example
    365 days), even for a monitor that is 2 days old. The backend computes a
    check-based uptime. The UI can show two different numbers.
11. **Cleanup loads every row before it deletes it.**
    `deleteByTenantIdAndCheckedAtBefore` (`CheckResultRepository.java:98`) and
    `deleteByTenantIdAndCalculatedAtBefore` (`UptimeStatsRepository.java:80`)
    are Spring Data derived deletes. They load each entity and remove it one
    by one, in one transaction.
12. **Down-period logic exists two times.** `CheckResultService.java:183` and
    `CheckResultService.java:323` are copies.
13. **Time zone depends on the JVM.** Check times and windows use
    `LocalDateTime.now()` (JVM default zone). The JDBC URL uses
    `serverTimezone=UTC`. A JVM not in UTC shifts all windows. There is no
    injected `Clock`, so tests cannot control time.
14. **`TIMESTAMP` columns end in 2038.** `check_results.checked_at` and other
    columns are MariaDB `TIMESTAMP` (`V0_0_1__init.sql`).
15. **Native SQL is not tested.** Tests use H2 with Flyway off
    (`backend/src/test/resources/application-test.yml`). The MariaDB
    migrations and MariaDB-specific SQL never run in tests.

16. **SILENT monitors get no stats.** The checker runs ACTIVE and SILENT
    monitors (`MonitorExecutionService.java:159-165`). The stats job only
    runs ACTIVE monitors (`MonitorService.java:86`). SILENT monitors show old
    or missing stats.

## Found during rollout

17. **Our own errors were saved as downtime.** When saving a check result
    failed (for example no free database connection), `executeMonitorCheck`
    saved a second, DOWN result with the error text. A problem of Status Tacos
    then looked like an outage of the monitored service (seen on stage
    2026-10-07 20:41 UTC, the row was deleted by hand). Fixed: HTTP check
    errors still count as DOWN; save errors are only logged.

## Fix options

Chosen direction: option B. See [uptime-stats-plan.md](uptime-stats-plan.md).

- **A: fix the logic only.** Keep raw data for 365 days (or drop the 365d
  period). Use a fixed key per monitor and period so there is one row each.
  Add a distributed lock for the scheduled jobs.
- **B: rebuild the roll-up.** Store small hourly and daily aggregates. Build
  the 7d, 90d and 365d stats from them. Raw `check_results` can stay at 90
  days. Also fixes issue 2.
