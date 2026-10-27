-- Files of the public status page: fonts and images
-- This migration:
-- 1. Creates tenant_status_page_assets: files that a tenant uploads for its status page. The
--    server serves them itself, so the page needs no third-party host (GDPR). The CSS and the
--    template use them as asset:<name>; the server writes the URL in their place.

CREATE TABLE tenant_status_page_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size INT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    data MEDIUMBLOB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_tenant_status_page_assets_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_tenant_status_page_assets_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
