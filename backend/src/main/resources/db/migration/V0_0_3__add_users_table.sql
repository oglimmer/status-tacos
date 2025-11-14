-- Add users table for OIDC authentication and tenant mapping
CREATE TABLE users (
    id bigint PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL UNIQUE,
    oidc_subject VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    tenant_id bigint NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT,
    INDEX idx_user_email (email),
    INDEX idx_user_oidc_subject (oidc_subject),
    INDEX idx_user_tenant (tenant_id)
);

-- Create a default user for tenant 1 for testing/setup
INSERT INTO users (email, oidc_subject, first_name, last_name, tenant_id, is_active)
VALUES ('admin@example.com', 'default-admin-subject', 'Admin', 'User', 1, TRUE);
