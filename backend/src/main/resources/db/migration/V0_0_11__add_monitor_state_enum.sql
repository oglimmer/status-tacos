-- Add monitor state enum to replace boolean is_active field
-- States: ACTIVE (monitored with alerts), SILENT (monitored without alerts), INACTIVE (not monitored)

-- Add the new state column with enum values
ALTER TABLE monitors
    ADD COLUMN state ENUM('ACTIVE', 'SILENT', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE' AFTER is_active;

-- Migrate existing data: is_active=1 -> ACTIVE, is_active=0 -> INACTIVE
UPDATE monitors SET state = 'ACTIVE' WHERE is_active = 1;
UPDATE monitors SET state = 'INACTIVE' WHERE is_active = 0;

-- Drop the old is_active column
ALTER TABLE monitors DROP COLUMN is_active;

-- Add index on the new state column for query performance
ALTER TABLE monitors ADD INDEX idx_monitors_state (state);
