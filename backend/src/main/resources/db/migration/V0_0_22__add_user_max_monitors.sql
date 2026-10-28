-- Maximum number of monitors per user
-- This migration:
-- 1. Adds users.max_monitors. NULL: the server default (monitor.limits.max-monitors-per-user).
--    0: no limit. The default has no limit; the hosted instance sets 3. An admin sets a higher value for single users in the database.

ALTER TABLE users ADD COLUMN max_monitors INT NULL;
