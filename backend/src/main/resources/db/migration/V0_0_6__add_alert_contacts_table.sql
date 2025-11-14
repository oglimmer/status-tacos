-- Add alert_contacts table and migrate existing monitor email contacts
-- This migration will:
-- 1. Create the alert_contacts table
-- 2. Migrate existing monitor email_contact data to alert_contacts
-- 3. Remove the email_contact column from monitors table

-- Create alert_contacts table
CREATE TABLE alert_contacts (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                tenant_id BIGINT NOT NULL,
                                type VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
                                value VARCHAR(320) NOT NULL,
                                name VARCHAR(100),
                                is_active TINYINT(1) NOT NULL DEFAULT 1,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                CONSTRAINT fk_alert_contacts_tenant
                                    FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,

                                CONSTRAINT unique_tenant_type_value
                                    UNIQUE (tenant_id, type, value)
);

-- Create indexes for performance
CREATE INDEX idx_alert_contacts_tenant_id ON alert_contacts(tenant_id);
CREATE INDEX idx_alert_contacts_tenant_active ON alert_contacts(tenant_id, is_active);

-- Convert all the older tables to the newer collation
ALTER TABLE check_results CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE cleanup_jobs CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE uptime_stats CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE alert_history CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE monitors CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE alert_contacts CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE monitor_status CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;


-- Migrate existing monitor email_contact data to alert_contacts
-- This creates one alert contact per tenant for each unique email found in monitors
INSERT INTO alert_contacts (tenant_id, type, value, name, is_active, created_at, updated_at)
SELECT DISTINCT
    m.tenant_id,
    'EMAIL',
    m.email_contact,
    CONCAT('Migrated'),
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM monitors m
WHERE m.email_contact IS NOT NULL
  AND m.email_contact != ''
  AND NOT EXISTS (
    SELECT 1 FROM alert_contacts ac
    WHERE ac.tenant_id = m.tenant_id
      AND ac.type = 'EMAIL'
      AND ac.value = m.email_contact
);

-- Remove the email_contact column from monitors table
ALTER TABLE monitors DROP COLUMN email_contact;
