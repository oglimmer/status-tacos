-- Manual check ("check now")
-- A user can ask for a check of a monitor now. The check job does it in its next run (at most one
-- run interval later), also when the monitor is not due in that run.
-- This migration:
-- 1. Adds monitors.check_requested_at: when a user asked for a check. NULL: no open request. The
--    check job sets it back to NULL after the check.
-- 2. Adds check_results.up_adjust_seconds. A manual check adds no time to the uptime
--    (check_interval = 0). It takes the rest of the time of the scheduled check before it and moves
--    that time to its own state: +n UP seconds after a DOWN check, -n after an UP check, 0 for the
--    same state. 0 for all scheduled checks.
-- 3. Makes check_rollup_hourly.up_seconds signed: with a negative adjust, the up_seconds of one
--    hour can be below 0.

ALTER TABLE monitors
    ADD COLUMN check_requested_at TIMESTAMP NULL DEFAULT NULL AFTER environment;

ALTER TABLE check_results
    ADD COLUMN up_adjust_seconds INT NOT NULL DEFAULT 0 AFTER check_interval;

ALTER TABLE check_rollup_hourly
    MODIFY COLUMN up_seconds BIGINT NOT NULL DEFAULT 0;
