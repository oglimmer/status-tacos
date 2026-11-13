-- Environment of a monitor
-- This migration adds a label for the environment of the system that the monitor checks.
-- 1. environment: DEV, STAGE or PROD. It is only a label, it does not change the checks or alerts.
-- NULL when the environment is not set. Existing monitors stay NULL.

ALTER TABLE monitors
    ADD COLUMN environment VARCHAR(10) NULL DEFAULT NULL AFTER daily_pause_timezone;
