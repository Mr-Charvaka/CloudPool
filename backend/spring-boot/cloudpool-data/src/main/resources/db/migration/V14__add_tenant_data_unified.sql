CREATE TABLE tenant_data (
    id VARCHAR(36) PRIMARY KEY,
    project_id UUID NOT NULL,
    table_id UUID NOT NULL,
    user_id UUID NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_data_project_id ON tenant_data (project_id);
CREATE INDEX idx_tenant_data_table_id ON tenant_data (table_id);
CREATE INDEX idx_tenant_data_user_id ON tenant_data (user_id);
