CREATE TABLE kv_store (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    key_name VARCHAR(255) NOT NULL,
    kv_value TEXT NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_kv_project_key UNIQUE (project_id, key_name)
);

CREATE INDEX idx_kv_store_project ON kv_store(project_id);
CREATE INDEX idx_kv_store_expires ON kv_store(expires_at);
