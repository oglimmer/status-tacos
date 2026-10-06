-- Microsoft Teams alert contacts and alert contacts limited to selected monitors
-- This migration:
-- 1. Widens alert_contacts.value, because Teams (Power Automate) workflow URLs are 250-300+ characters
--    long. The unique constraint is re-created as a HASH key, because a plain BTREE key on
--    (tenant_id, type, VARCHAR(2048) utf8mb4) is longer than the 3072 bytes InnoDB allows.
-- 2. Adds all_monitors: 1 = the contact is alerted for every monitor of its tenant (old behaviour),
--    0 = the contact is alerted only for the monitors listed in alert_contact_monitors.
-- 3. Creates alert_contact_monitors.

ALTER TABLE alert_contacts DROP INDEX unique_tenant_type_value;

ALTER TABLE alert_contacts MODIFY COLUMN `value` VARCHAR(2048) NOT NULL;

ALTER TABLE alert_contacts
ADD CONSTRAINT unique_tenant_type_value UNIQUE (tenant_id, type, `value`) USING HASH;

ALTER TABLE alert_contacts
ADD COLUMN all_monitors TINYINT(1) NOT NULL DEFAULT 1;

CREATE TABLE alert_contact_monitors (
    alert_contact_id INT NOT NULL,
    monitor_id INT UNSIGNED NOT NULL,

    PRIMARY KEY (alert_contact_id, monitor_id),

    CONSTRAINT fk_alert_contact_monitors_contact
        FOREIGN KEY (alert_contact_id) REFERENCES alert_contacts(id) ON DELETE CASCADE,

    CONSTRAINT fk_alert_contact_monitors_monitor
        FOREIGN KEY (monitor_id) REFERENCES monitors(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;

CREATE INDEX idx_alert_contact_monitors_monitor ON alert_contact_monitors(monitor_id);
