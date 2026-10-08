-- iOS push alerts
-- This migration:
-- 1. Adds alert_contacts.user_id: the owner of an IOS_PUSH alert contact. Only the owner manages it
--    (in the iOS app) and only the iOS devices of the owner get its alerts. NULL for other types.
-- 2. Creates push_devices: the APNs device tokens of the iOS app, one row per app install. A token
--    belongs to one user: when another user signs in on the device, the token moves to that user.

ALTER TABLE alert_contacts
ADD COLUMN user_id BIGINT NULL,
ADD CONSTRAINT fk_alert_contacts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE TABLE push_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(200) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    device_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_push_devices_token UNIQUE (token),

    CONSTRAINT fk_push_devices_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;

CREATE INDEX idx_push_devices_user ON push_devices(user_id);
