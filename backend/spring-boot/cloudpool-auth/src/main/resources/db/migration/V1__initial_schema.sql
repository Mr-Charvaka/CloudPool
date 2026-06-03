-- V1: Complete initial schema (includes all columns through V4)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN DEFAULT true,
    last_login_at TIMESTAMP,
    google_access_token VARCHAR(1000),
    google_refresh_token VARCHAR(1000),
    google_token_expires_at TIMESTAMP,
    custom_client_id VARCHAR(500),
    custom_client_secret VARCHAR(500),
    current_usage BIGINT DEFAULT 0,
    storage_quota BIGINT DEFAULT 5368709120,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS buckets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    is_public BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS files (
    id UUID PRIMARY KEY,
    bucket_id UUID NOT NULL REFERENCES buckets(id) ON DELETE CASCADE,
    name VARCHAR(512) NOT NULL,
    original_name VARCHAR(512) NOT NULL,
    size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    extension VARCHAR(10),
    drive_location VARCHAR(512),
    drive_file_id VARCHAR(512),
    is_public BOOLEAN DEFAULT false,
    is_encrypted BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS file_shares (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL,
    shared_with_email VARCHAR(255),
    permission VARCHAR(50) NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS developer_tables (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    project_id UUID,
    name VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS developer_table_fields (
    id UUID PRIMARY KEY,
    table_id UUID NOT NULL REFERENCES developer_tables(id) ON DELETE CASCADE,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50) NOT NULL,
    is_required BOOLEAN DEFAULT false
);

CREATE TABLE IF NOT EXISTS database_connections (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    db_type VARCHAR(50) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    database_name VARCHAR(255),
    username VARCHAR(255),
    password VARCHAR(255),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS project_secrets (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    secret_key VARCHAR(255) NOT NULL,
    secret_value VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS project_snapshots (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    topology_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    active BOOLEAN DEFAULT true,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(100),
    details VARCHAR(2000),
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS vector_collections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    dimension INTEGER NOT NULL,
    distance_metric VARCHAR(50) DEFAULT 'cosine',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS vector_documents (
    id UUID PRIMARY KEY,
    collection_id UUID NOT NULL REFERENCES vector_collections(id) ON DELETE CASCADE,
    doc_id VARCHAR(255) NOT NULL,
    content TEXT,
    embedding_vector BYTEA,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
