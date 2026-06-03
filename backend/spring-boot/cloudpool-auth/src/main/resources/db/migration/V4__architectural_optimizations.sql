-- V4: These columns are already included in V1 for fresh installs.
-- For existing DBs that ran the old V1 without them, add them safely.
ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_client_id VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_client_secret VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS current_usage BIGINT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS storage_quota BIGINT DEFAULT 5368709120;
