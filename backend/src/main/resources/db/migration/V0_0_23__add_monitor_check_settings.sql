-- Check settings per monitor
-- This migration:
-- 1. Adds monitors.check_interval: seconds between two checks, a multiple of 15. 15 for all
--    existing monitors, the interval they had until now.
-- 2. Adds monitors.connect_timeout: seconds to wait for the TCP connect. 5 for all existing
--    monitors, the value of monitor.http.connect-timeout until now.

ALTER TABLE monitors
    ADD COLUMN check_interval INT NOT NULL DEFAULT 15 AFTER prometheus_max_value,
    ADD COLUMN connect_timeout INT NOT NULL DEFAULT 5 AFTER check_interval;
