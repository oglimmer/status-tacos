-- Roll-ups of check_results for the uptime stats (7 and 90 days).
-- All times are UTC. Buckets are half-open: [bucket_start, bucket_start + 1 hour / 1 day).
-- The tables have no tenant_id: access is checked through the monitor, so a monitor move
-- needs no update here.

-- One row per monitor and hour. Response-time columns count successful checks only.
CREATE TABLE check_rollup_hourly
(
    monitor_id   INT UNSIGNED    NOT NULL,
    bucket_start DATETIME        NOT NULL,
    total_checks INT UNSIGNED    NOT NULL,
    up_checks    INT UNSIGNED    NOT NULL,
    rt_count     INT UNSIGNED    NOT NULL,
    rt_sum       BIGINT UNSIGNED NOT NULL,
    rt_min       INT UNSIGNED    NULL,
    rt_max       INT UNSIGNED    NULL,

    PRIMARY KEY (monitor_id, bucket_start),
    INDEX idx_rollup_hourly_bucket (bucket_start),
    CONSTRAINT fk_rollup_hourly_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Response-time histogram of successful checks per monitor and hour.
-- rt_bucket = FLOOR(LN(GREATEST(response_time_ms, 1)) / LN(1.05)).
CREATE TABLE check_rollup_hourly_histogram
(
    monitor_id   INT UNSIGNED      NOT NULL,
    bucket_start DATETIME          NOT NULL,
    rt_bucket    SMALLINT UNSIGNED NOT NULL,
    cnt          INT UNSIGNED      NOT NULL,

    PRIMARY KEY (monitor_id, bucket_start, rt_bucket),
    INDEX idx_rollup_hourly_hist_bucket (bucket_start),
    CONSTRAINT fk_rollup_hourly_hist_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Same histogram per monitor and UTC day, summed from the hourly histogram.
CREATE TABLE check_rollup_daily_histogram
(
    monitor_id   INT UNSIGNED      NOT NULL,
    bucket_start DATETIME          NOT NULL,
    rt_bucket    SMALLINT UNSIGNED NOT NULL,
    cnt          INT UNSIGNED      NOT NULL,

    PRIMARY KEY (monitor_id, bucket_start, rt_bucket),
    INDEX idx_rollup_daily_hist_bucket (bucket_start),
    CONSTRAINT fk_rollup_daily_hist_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Down periods. started_at = first DOWN check, ended_at = first UP check after it.
-- ended_at IS NULL = the outage is still open. open_flag makes sure that a monitor has at most
-- one open outage (a UNIQUE key allows many NULLs).
CREATE TABLE monitor_outage
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    monitor_id INT UNSIGNED    NOT NULL,
    started_at DATETIME        NOT NULL,
    ended_at   DATETIME        NULL,
    open_flag  TINYINT AS (IF(ended_at IS NULL, 1, NULL)) PERSISTENT,

    PRIMARY KEY (id),
    UNIQUE KEY uk_outage_one_open (monitor_id, open_flag),
    INDEX idx_outage_monitor_start (monitor_id, started_at),
    INDEX idx_outage_ended (ended_at),
    CONSTRAINT fk_outage_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Watermarks of the roll-up job (exclusive). Everything before done_until is rolled up.
-- NULL = nothing rolled up yet.
CREATE TABLE rollup_state
(
    name       VARCHAR(32) NOT NULL,
    done_until DATETIME    NULL,

    PRIMARY KEY (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO rollup_state (name, done_until)
VALUES ('hourly', NULL),
       ('daily', NULL);

-- ShedLock: only one replica runs a scheduled job at a time.
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,

    PRIMARY KEY (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
