-- DNS mode per monitor
-- This migration:
-- 1. Adds monitors.use_system_dns: TRUE asks the DNS server of the backend, with its cache.
--    FALSE (the default) asks the nameservers of the domain of the monitor directly.

ALTER TABLE monitors
    ADD COLUMN use_system_dns BOOLEAN NOT NULL DEFAULT FALSE AFTER connect_timeout;
