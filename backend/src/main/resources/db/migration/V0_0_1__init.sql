-- StatusTaco MVP Database Schema - MariaDB Best Practices

-- Set storage engine and character set
SET default_storage_engine = InnoDB;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Stores monitor configurations
CREATE TABLE monitors
(
    id            INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    name          VARCHAR(255)  NOT NULL,
    url           VARCHAR(2048) NOT NULL,
    email_contact VARCHAR(320)  NOT NULL, -- RFC 5321 max email length
    is_active     TINYINT(1)    NOT NULL DEFAULT 1,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_monitors_active (is_active),
    INDEX idx_monitors_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC;

-- Stores individual check results
CREATE TABLE check_results
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    monitor_id       INT UNSIGNED    NOT NULL,
    checked_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status_code      integer UNSIGNED,
    response_time_ms integer UNSIGNED,
    is_up            TINYINT(1)      NOT NULL,
    error_message    TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY fk_check_monitor (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE,
    INDEX idx_check_monitor_time (monitor_id, checked_at),
    INDEX idx_check_time (checked_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC;

-- Stores current status of each monitor (for quick dashboard queries)
CREATE TABLE monitor_status
(
    monitor_id            INT UNSIGNED        NOT NULL,
    current_status        ENUM ('up', 'down') NOT NULL,
    last_checked_at       TIMESTAMP           NULL,
    last_up_at            TIMESTAMP           NULL,
    last_down_at          TIMESTAMP           NULL,
    consecutive_failures  integer UNSIGNED   NOT NULL DEFAULT 0,
    last_response_time_ms integer UNSIGNED,
    last_status_code      integer UNSIGNED,
    updated_at            TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (monitor_id),
    FOREIGN KEY fk_status_monitor (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE,
    INDEX idx_status_current (current_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC;

-- Stores sent alerts to prevent spam
CREATE TABLE alert_history
(
    id            BIGINT UNSIGNED                      NOT NULL AUTO_INCREMENT,
    monitor_id    INT UNSIGNED                         NOT NULL,
    alert_type    ENUM ('down', 'up', 'slow_response') NOT NULL,
    sent_at       TIMESTAMP                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    email_sent_to VARCHAR(320)                         NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY fk_alert_monitor (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE,
    INDEX idx_alert_monitor_sent (monitor_id, sent_at),
    INDEX idx_alert_sent (sent_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC;

-- Pre-computed uptime statistics (updated periodically for performance)
CREATE TABLE uptime_stats
(
    id                   BIGINT UNSIGNED    NOT NULL AUTO_INCREMENT,
    monitor_id           INT UNSIGNED       NOT NULL,
    period_type          ENUM ('24h', '7d') NOT NULL,
    period_start         TIMESTAMP          NOT NULL,
    period_end           TIMESTAMP          NOT NULL,
    total_checks         MEDIUMINT UNSIGNED NOT NULL,
    successful_checks    MEDIUMINT UNSIGNED NOT NULL,
    uptime_percentage    DECIMAL(5, 2)      NOT NULL,
    avg_response_time_ms integer UNSIGNED,
    calculated_at        TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    FOREIGN KEY fk_stats_monitor (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE,
    UNIQUE KEY uk_monitor_period (monitor_id, period_type, period_start),
    INDEX idx_stats_monitor_period (monitor_id, period_type),
    INDEX idx_stats_calculated (calculated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC;

-- Sample data cleanup job tracking (for 90-day retention)
CREATE TABLE cleanup_jobs
(
    id                integer UNSIGNED                       NOT NULL AUTO_INCREMENT,
    job_type          VARCHAR(50)                             NOT NULL,
    last_run_at       TIMESTAMP                               NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    records_deleted   INT UNSIGNED                            NOT NULL DEFAULT 0,
    execution_time_ms integer UNSIGNED,
    status            ENUM ('running', 'completed', 'failed') NOT NULL DEFAULT 'completed',

    PRIMARY KEY (id),
    UNIQUE KEY uk_job_type (job_type),
    INDEX idx_cleanup_last_run (last_run_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC;

# -- Create initial cleanup job records
# INSERT INTO cleanup_jobs (job_type, records_deleted)
# VALUES ('check_results_cleanup', 0),
#        ('alert_history_cleanup', 0),
#        ('uptime_stats_cleanup', 0)
# ON DUPLICATE KEY UPDATE job_type = VALUES(job_type);

-- Performance optimization settings
-- Add these to your MariaDB configuration (my.cnf)
/*
[mysqld]
# InnoDB settings for better performance
innodb_buffer_pool_size = 70% of available RAM
innodb_log_file_size = 256M
innodb_flush_log_at_trx_commit = 2
innodb_flush_method = O_DIRECT

# Query cache (if using MariaDB < 10.10)
query_cache_type = 1
query_cache_size = 64M

# Connection settings
max_connections = 151
thread_cache_size = 8

# Logging for monitoring
slow_query_log = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 2
log_queries_not_using_indexes = 1
*/
