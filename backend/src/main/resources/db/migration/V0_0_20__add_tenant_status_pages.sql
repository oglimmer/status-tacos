-- Branded public status pages
-- This migration:
-- 1. Creates tenant_status_pages: the custom HTML template and CSS of the public status page of a
--    tenant. No row, or a NULL column, means the built-in default. The server fills the template
--    (Mustache), cleans the HTML and serves it with a sandbox CSP: no scripts run on the page.

CREATE TABLE tenant_status_pages (
    tenant_id BIGINT PRIMARY KEY,
    html_template MEDIUMTEXT NULL,
    css MEDIUMTEXT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tenant_status_pages_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
