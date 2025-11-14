-- Fix period_type column to allow longer enum values
-- Change from ENUM to VARCHAR to support Java enum names

ALTER TABLE uptime_stats
MODIFY COLUMN period_type VARCHAR(20) NOT NULL;
