-- Add HTTP-specific fields to alert_contacts table for HTTP alert contact type
-- This migration adds:
-- 1. http_method column for GET/POST methods
-- 2. http_headers column for custom headers (JSON format)
-- 3. http_body column for POST request body

ALTER TABLE alert_contacts
ADD COLUMN http_method VARCHAR(10) NULL,
ADD COLUMN http_headers TEXT NULL,
ADD COLUMN http_body TEXT NULL,
ADD COLUMN http_content_type VARCHAR(50) NULL;
