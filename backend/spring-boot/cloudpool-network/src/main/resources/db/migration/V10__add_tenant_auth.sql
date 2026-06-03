CREATE TABLE tenant_users (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_user_email UNIQUE (project_id, email)
);

CREATE TABLE tenant_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tenant_users(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    refresh_token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_users_project ON tenant_users(project_id);
CREATE INDEX idx_tenant_sessions_token ON tenant_sessions(refresh_token);
