-- V17: Add indexes to user_id foreign keys to prevent full table scans

CREATE INDEX IF NOT EXISTS idx_projects_user_id ON projects(user_id);
CREATE INDEX IF NOT EXISTS idx_buckets_user_id ON buckets(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_vector_collections_user_id ON vector_collections(user_id);
CREATE INDEX IF NOT EXISTS idx_developer_tables_user_id ON developer_tables(user_id);
