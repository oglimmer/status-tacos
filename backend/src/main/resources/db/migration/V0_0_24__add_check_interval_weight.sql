-- Time-weighted uptime
-- Uptime was successful checks / all checks. With a check interval per monitor, a check can stand
-- for 15 s or for 1 hour, so a change of the interval made the uptime wrong. Now each check counts
-- with its interval: uptime = up_seconds / total_seconds.
-- This migration:
-- 1. Adds check_results.check_interval: seconds between this check and the next one. 15 for all
--    existing rows, the interval of all monitors until V0_0_23.
-- 2. Adds check_rollup_hourly.total_seconds and up_seconds: the sums of check_interval of all
--    checks and of the successful checks. Existing rows get 15 s per check.

ALTER TABLE check_results
    ADD COLUMN check_interval INT UNSIGNED NOT NULL DEFAULT 15 AFTER is_up;

ALTER TABLE check_rollup_hourly
    ADD COLUMN total_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER up_checks,
    ADD COLUMN up_seconds    BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER total_seconds;

UPDATE check_rollup_hourly
SET total_seconds = total_checks * 15,
    up_seconds    = up_checks * 15;
