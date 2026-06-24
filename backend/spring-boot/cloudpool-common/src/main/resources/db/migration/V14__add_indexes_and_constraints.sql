-- Missing indexes for foreign keys and frequently queried columns
CREATE INDEX IF NOT EXISTS idx_file_metadata_bucket_id ON file_metadata(bucket_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_user_id ON file_metadata(bucket_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_created_at ON file_metadata(created_at);
CREATE INDEX IF NOT EXISTS idx_file_metadata_deleted_at ON file_metadata(deleted_at);
CREATE INDEX IF NOT EXISTS idx_file_shares_file_id ON file_shares(file_id);
CREATE INDEX IF NOT EXISTS idx_file_shares_token ON file_shares(share_token);
CREATE INDEX IF NOT EXISTS idx_vector_documents_collection_id ON vector_documents(collection_id);
CREATE INDEX IF NOT EXISTS idx_vector_collections_tenant_id ON vector_collections(tenant_id);
CREATE INDEX IF NOT EXISTS idx_container_deployments_user_id ON container_deployments(user_id);
CREATE INDEX IF NOT EXISTS idx_background_jobs_status ON background_jobs(status);
CREATE INDEX IF NOT EXISTS idx_background_jobs_scheduled_at ON background_jobs(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_expires_at ON api_keys(expires_at);
CREATE INDEX IF NOT EXISTS idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_user_id ON payment_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_status ON payment_transactions(status);
CREATE INDEX IF NOT EXISTS idx_inbox_events_user_id ON inbox_events(user_id);
CREATE INDEX IF NOT EXISTS idx_inbox_events_processed ON inbox_events(processed);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- NOT NULL constraints (enforce at DB level what the app already assumes)
ALTER TABLE file_metadata ALTER COLUMN checksum SET NOT NULL;
ALTER TABLE file_metadata ALTER COLUMN bucket_id SET NOT NULL;
ALTER TABLE vector_documents ALTER COLUMN collection_id SET NOT NULL;
ALTER TABLE vector_documents ALTER COLUMN content SET NOT NULL;
ALTER TABLE container_deployments ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE container_deployments ALTER COLUMN image SET NOT NULL;
ALTER TABLE api_keys ALTER COLUMN key_hash SET NOT NULL;
ALTER TABLE api_keys ALTER COLUMN user_id SET NOT NULL;

-- CHECK constraints
ALTER TABLE file_metadata ADD CONSTRAINT chk_file_size_positive CHECK (size > 0);
ALTER TABLE api_keys ADD CONSTRAINT chk_api_key_expires_future CHECK (expires_at > created_at);
ALTER TABLE container_deployments ADD CONSTRAINT chk_replicas_positive CHECK (replicas > 0);

-- UNIQUE constraints
ALTER TABLE api_keys ADD CONSTRAINT uq_api_key_hash UNIQUE (key_hash);
ALTER TABLE file_shares ADD CONSTRAINT uq_share_token UNIQUE (share_token);