-- Create junction table for many-to-many relationship between users and tenants
CREATE TABLE user_tenant (
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tenant_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
);

-- Migrate existing data: move current user-tenant relationships to junction table
INSERT INTO user_tenant (user_id, tenant_id, created_at)
SELECT id, tenant_id, created_at FROM users WHERE tenant_id IS NOT NULL;

-- Remove the foreign key constraint from users table
ALTER TABLE users DROP FOREIGN KEY IF EXISTS fk_user_tenant;

-- Drop the tenant_id column from users table
ALTER TABLE users DROP COLUMN tenant_id;
