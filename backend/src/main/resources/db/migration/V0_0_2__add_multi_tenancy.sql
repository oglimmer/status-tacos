-- Add multi-tenancy support
-- This migration adds tenant support to all entities

-- Create tenant table
CREATE TABLE tenant (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert a default tenant for existing data
INSERT INTO tenant (name, code, description, is_active, created_at, updated_at)
VALUES ('Default Tenant', 'default', 'Default tenant for existing data', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Add tenant_id column to monitors table
ALTER TABLE monitors ADD COLUMN tenant_id bigint NOT NULL;

ALTER TABLE monitors ADD CONSTRAINT fk_monitor_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Add index for monitors tenant
CREATE INDEX idx_monitors_tenant ON monitors(tenant_id);

-- Add tenant_id column to check_results table
ALTER TABLE check_results ADD COLUMN tenant_id bigint NOT NULL;

ALTER TABLE check_results ADD CONSTRAINT fk_check_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Add index for check_results tenant
CREATE INDEX idx_check_tenant ON check_results(tenant_id);

-- Add tenant_id column to monitor_status table
ALTER TABLE monitor_status ADD COLUMN tenant_id bigint NOT NULL;


ALTER TABLE monitor_status ADD CONSTRAINT fk_status_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Add index for monitor_status tenant
CREATE INDEX idx_status_tenant ON monitor_status(tenant_id);

-- Add tenant_id column to alert_history table
ALTER TABLE alert_history ADD COLUMN tenant_id bigint NOT NULL;


ALTER TABLE alert_history ADD CONSTRAINT fk_alert_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Add index for alert_history tenant
CREATE INDEX idx_alert_tenant ON alert_history(tenant_id);

-- Add tenant_id column to uptime_stats table
ALTER TABLE uptime_stats ADD COLUMN tenant_id bigint NOT NULL;


ALTER TABLE uptime_stats ADD CONSTRAINT fk_stats_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Add index for uptime_stats tenant
CREATE INDEX idx_stats_tenant ON uptime_stats(tenant_id);

-- Update cleanup_jobs table for multi-tenancy
ALTER TABLE cleanup_jobs ADD COLUMN tenant_id bigint NOT NULL;

ALTER TABLE cleanup_jobs ADD CONSTRAINT fk_cleanup_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Add index for cleanup_jobs tenant
CREATE INDEX idx_cleanup_tenant ON cleanup_jobs(tenant_id);

-- Update unique constraint for cleanup_jobs to include tenant
ALTER TABLE cleanup_jobs DROP CONSTRAINT uk_job_type;
ALTER TABLE cleanup_jobs ADD CONSTRAINT uk_tenant_job_type UNIQUE (tenant_id, job_type);
