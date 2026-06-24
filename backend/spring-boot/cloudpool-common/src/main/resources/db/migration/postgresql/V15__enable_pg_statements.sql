-- Postgres-only: pg_stat_statements for query performance monitoring
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Create a view for easy slow query analysis
CREATE OR REPLACE VIEW slow_queries AS
SELECT
    queryid,
    query,
    calls,
    total_exec_time,
    mean_exec_time,
    rows,
    shared_blks_hit,
    shared_blks_read
FROM pg_stat_statements
WHERE mean_exec_time > 1000
ORDER BY total_exec_time DESC;