CREATE TABLE IF NOT EXISTS static_sites (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    bucket_name VARCHAR(100) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS serverless_functions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    trigger_route VARCHAR(255) NOT NULL,
    code TEXT NOT NULL,
    wasm_compiled BOOLEAN DEFAULT FALSE,
    status VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS container_deployments (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    docker_image VARCHAR(255) NOT NULL,
    cpu DOUBLE PRECISION NOT NULL,
    memory INTEGER NOT NULL,
    replicas INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    logs TEXT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
