-- Error of the last check in monitor_status
-- This migration:
-- 1. Adds monitor_status.last_error_message: why the last check failed, NULL when it was UP. The
--    dashboard shows it when the check got no HTTP status code (DNS, TLS or network error).
-- 2. Fills it for the monitors that are down now, from their last check.

ALTER TABLE monitor_status
    ADD COLUMN last_error_message VARCHAR(1000) NULL DEFAULT NULL AFTER last_status_code;

UPDATE monitor_status ms
    JOIN check_results cr ON cr.monitor_id = ms.monitor_id AND cr.checked_at = ms.last_checked_at
SET ms.last_error_message = LEFT(cr.error_message, 1000)
WHERE ms.current_status = 'down';
