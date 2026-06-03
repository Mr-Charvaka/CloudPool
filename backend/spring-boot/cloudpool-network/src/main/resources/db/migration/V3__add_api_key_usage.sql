CREATE TABLE IF NOT EXISTS api_key_usage_logs (
    id UUID PRIMARY KEY,
    api_key_id UUID NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    endpoint VARCHAR(255) NOT NULL,
    method VARCHAR(10) NOT NULL,
    status_code INTEGER NOT NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_api_key_usage_logs_key_id ON api_key_usage_logs(api_key_id);
CREATE INDEX IF NOT EXISTS idx_api_key_usage_logs_created_at ON api_key_usage_logs(created_at DESC);
