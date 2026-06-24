-- Enable SSL enforcement (requires SSL connections)
ALTER SYSTEM SET ssl = on;

-- Log queries slower than 1 second
ALTER SYSTEM SET log_min_duration_statement = 1000;

-- Log connection attempts
ALTER SYSTEM SET log_connections = on;
ALTER SYSTEM SET log_disconnections = on;
ALTER SYSTEM SET log_checkpoints = on;
ALTER SYSTEM SET log_lock_waits = on;

-- Track query statistics
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Reload configuration
SELECT pg_reload_conf();