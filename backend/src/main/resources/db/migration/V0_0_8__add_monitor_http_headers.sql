-- Add HTTP headers support to monitors table
-- This migration adds:
-- 1. http_headers column for custom HTTP headers (JSON format)
-- Allows monitors to include custom headers when making HTTP requests
--
-- The JSON column type is supported in MariaDB 10.2+ and MySQL 5.7+
-- JSON validates the data format and provides efficient storage and indexing

ALTER TABLE monitors
ADD COLUMN http_headers JSON NULL COMMENT 'Custom HTTP headers for monitor requests stored as JSON key-value pairs';
