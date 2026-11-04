-- Silent monitors end their silence
-- This migration:
-- 1. Adds monitors.silent_until: the end of the silence of a SILENT monitor. At that time the
--    monitor is ACTIVE again and sends alerts. NULL when the monitor is not SILENT.
-- 2. Gives the monitors that are SILENT now 7 days, the longest silence. Before, a silence did not
--    end.

ALTER TABLE monitors
    ADD COLUMN silent_until TIMESTAMP NULL DEFAULT NULL AFTER state;

UPDATE monitors
SET silent_until = UTC_TIMESTAMP() + INTERVAL 7 DAY
WHERE state = 'SILENT';

CREATE INDEX idx_monitors_silent_until ON monitors (silent_until);
