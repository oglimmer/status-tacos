-- Add missing columns to uptime_stats table
ALTER TABLE `status-tacos`.uptime_stats
    ADD COLUMN min_response_time_ms INT UNSIGNED NULL AFTER uptime_percentage,
    ADD COLUMN max_response_time_ms INT UNSIGNED NULL AFTER min_response_time_ms,
    ADD COLUMN p99_response_time_ms INT UNSIGNED NULL AFTER avg_response_time_ms,
    ADD COLUMN response_time_data longtext NULL AFTER p99_response_time_ms,
    ADD COLUMN status_change_data longtext NULL AFTER response_time_data;

ALTER TABLE uptime_stats
    MODIFY COLUMN period_type varchar(30) NOT NULL;
