-- Two Status Tacos servers that work together (peer)
-- The peer tenant (monitor.peer.tenant-id) has one monitor of the heartbeat of the other server.
-- This migration:
-- 1. Adds api_tokens.scope: GRAFANA tokens read /prom, PEER tokens call /peer of this server.
-- 2. Adds monitor_status.peer_result: what the other server saw when it checked a DOWN monitor
--    before the DOWN alert. NULL when the monitor is UP or the other server was not asked.
-- 3. Creates alert_channel_health: one row per alert channel (EMAIL, PUSH, WEBHOOK). When a
--    channel fails too often, the heartbeat tells the other server that this one cannot alert.

ALTER TABLE api_tokens
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'GRAFANA' AFTER token_prefix;

ALTER TABLE monitor_status
    ADD COLUMN peer_result VARCHAR(20) NULL DEFAULT NULL AFTER last_error_message;

CREATE TABLE alert_channel_health (
    channel VARCHAR(20) NOT NULL PRIMARY KEY,
    consecutive_failures INT NOT NULL DEFAULT 0,
    failed_hosts VARCHAR(1000) NULL,
    failing_since TIMESTAMP NULL,
    reason VARCHAR(500) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;

INSERT INTO alert_channel_health (channel) VALUES ('EMAIL'), ('PUSH'), ('WEBHOOK');
