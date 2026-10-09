-- Start of the outage in monitor_status
-- This migration:
-- 1. Adds monitor_status.outage_started_at: the first failed check of the current outage, or of the
--    last outage when the monitor is up again. The alerting threshold and the alert texts read it.
--    Before, each failed check searched check_results for the start, which read all rows of a
--    monitor that was never up.
-- 2. Fills it for the monitors that are down now: the first failed check after the last successful
--    check. A monitor that was never up is down since its first failed check.

ALTER TABLE monitor_status
    ADD COLUMN outage_started_at TIMESTAMP NULL DEFAULT NULL AFTER last_down_at;

UPDATE monitor_status ms
SET ms.outage_started_at = COALESCE(
        (SELECT MIN(cr.checked_at)
         FROM check_results cr
         WHERE cr.monitor_id = ms.monitor_id
           AND cr.is_up = 0
           AND cr.checked_at > COALESCE(
                 (SELECT MAX(cu.checked_at)
                  FROM check_results cu
                  WHERE cu.monitor_id = ms.monitor_id
                    AND cu.is_up = 1),
                 '1970-01-01 00:00:01')),
        ms.last_down_at)
WHERE ms.current_status = 'down';
