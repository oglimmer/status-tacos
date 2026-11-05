-- Daily pause of a monitor
-- This migration adds a time window each day without checks, for example for a nightly database
-- dump. In the window the monitor is not checked, so it has no DOWN results and sends no alerts.
-- 1. daily_pause_from: start of the window (inclusive), wall-clock time in daily_pause_timezone.
-- 2. daily_pause_until: end of the window (exclusive). Before daily_pause_from: past midnight.
-- 3. daily_pause_timezone: IANA time zone id, for example Europe/Berlin or UTC.
-- All three are NULL when the monitor has no daily pause.

ALTER TABLE monitors
    ADD COLUMN daily_pause_from TIME NULL DEFAULT NULL AFTER silent_until,
    ADD COLUMN daily_pause_until TIME NULL DEFAULT NULL AFTER daily_pause_from,
    ADD COLUMN daily_pause_timezone VARCHAR(64) NULL DEFAULT NULL AFTER daily_pause_until;
