-- Add alerting_threshold column to monitors table
-- This column stores the threshold in seconds before alerts are sent (default: 30, must be multiple of 15)

-- Add the alerting_threshold column
ALTER TABLE monitors
ADD COLUMN alerting_threshold INT NOT NULL DEFAULT 30
COMMENT 'Threshold in seconds before alerts are sent (must be multiple of 15)';

-- Add index for performance when querying by threshold
ALTER TABLE monitors
ADD INDEX idx_monitors_alerting_threshold (alerting_threshold);

-- Add constraint to ensure alerting_threshold is positive and a multiple of 15
ALTER TABLE monitors
ADD CONSTRAINT chk_alerting_threshold_multiple_of_15
CHECK (alerting_threshold > 0 AND alerting_threshold % 15 = 0);
