-- API tokens
-- A user signs in with Keycloak. A tool like Grafana cannot do that, so it sends an API token.
-- This migration creates api_tokens: one row per token. The table stores only the SHA-256 hash of
-- the token: the user sees the token once, when it is created.

CREATE TABLE api_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_prefix VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NULL,

    CONSTRAINT uk_api_tokens_hash UNIQUE (token_hash),

    CONSTRAINT fk_api_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;

CREATE INDEX idx_api_tokens_user ON api_tokens(user_id);
