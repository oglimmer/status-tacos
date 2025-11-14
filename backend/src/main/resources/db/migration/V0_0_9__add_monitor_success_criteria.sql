-- Add success criteria fields to monitors table
-- This migration adds:
-- 1. status_code_regex column for HTTP status code pattern matching (default: 2xx and 3xx)
-- 2. response_body_regex column for response body pattern matching
-- 3. prometheus_key column for Prometheus metric key
-- 4. prometheus_min_value and prometheus_max_value columns for Prometheus threshold evaluation
--
-- These fields allow users to define custom success criteria for their monitors:
-- - Option 1: HTTP status code regex (default: ^[23]\d{2}$ for 2xx and 3xx)
-- - Option 2: Response body regex matching
-- - Option 3: Prometheus metric evaluation with min/max thresholds

ALTER TABLE monitors
ADD COLUMN status_code_regex VARCHAR(500) DEFAULT '^[23]\\d{2}$' COMMENT 'Regex pattern for successful HTTP status codes',
ADD COLUMN response_body_regex VARCHAR(1000) NULL COMMENT 'Regex pattern to match against response body for success',
ADD COLUMN prometheus_key VARCHAR(255) NULL COMMENT 'Prometheus metric key for success evaluation',
ADD COLUMN prometheus_min_value DOUBLE NULL COMMENT 'Minimum value for Prometheus metric success',
ADD COLUMN prometheus_max_value DOUBLE NULL COMMENT 'Maximum value for Prometheus metric success';
