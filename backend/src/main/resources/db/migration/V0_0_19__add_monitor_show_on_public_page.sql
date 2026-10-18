-- Public status page
-- This migration:
-- 1. Adds monitors.show_on_public_page: when true, the public status page (no sign-in) shows the
--    name, the current status, the start of the current outage and the 90-day uptime of the
--    monitor. Nothing else of the monitor is public. Off for all existing monitors.

ALTER TABLE monitors
    ADD COLUMN show_on_public_page BOOLEAN NOT NULL DEFAULT FALSE AFTER alerting_threshold;
