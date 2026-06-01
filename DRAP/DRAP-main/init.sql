-- D-RAP Production Persistence Schema

-- Table to store active tunnel assignments
CREATE TABLE IF NOT EXISTS tunnels (
    subdomain VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_active TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Table to store captured HTTP request history
CREATE TABLE IF NOT EXISTS request_history (
    id UUID PRIMARY KEY,
    tunnel_id VARCHAR(64) REFERENCES tunnels(subdomain) ON DELETE CASCADE,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    method VARCHAR(16) NOT NULL,
    path TEXT NOT NULL,
    host TEXT NOT NULL,
    headers JSONB NOT NULL
);

-- Index for fast history lookups per tunnel
CREATE INDEX IF NOT EXISTS idx_request_history_tunnel_id ON request_history(tunnel_id);
CREATE INDEX IF NOT EXISTS idx_request_history_timestamp ON request_history(timestamp DESC);
