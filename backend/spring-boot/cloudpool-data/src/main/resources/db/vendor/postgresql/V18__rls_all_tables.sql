-- V18: Extend Row-Level Security to ALL tenant-scoped tables.
--
-- This completes what V15 started. Every table that has a direct or indirect
-- path to a user_id gets RLS enabled with FORCE ROW LEVEL SECURITY.
-- The TenantAwareDataSourceWrapper sets app.tenant_id at every connection.
-- Without it, current_setting(...) returns NULL and zero rows are returned
-- (fail-closed).

-- ── DIRECT user_id tables ──
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_projects ON projects
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE buckets ENABLE ROW LEVEL SECURITY;
ALTER TABLE buckets FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_buckets ON buckets
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE api_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_api_keys ON api_keys
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_audit_logs ON audit_logs
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE static_sites ENABLE ROW LEVEL SECURITY;
ALTER TABLE static_sites FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_static_sites ON static_sites
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE serverless_functions ENABLE ROW LEVEL SECURITY;
ALTER TABLE serverless_functions FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_serverless_functions ON serverless_functions
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE container_deployments ENABLE ROW LEVEL SECURITY;
ALTER TABLE container_deployments FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_container_deployments ON container_deployments
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE developer_tables ENABLE ROW LEVEL SECURITY;
ALTER TABLE developer_tables FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_developer_tables ON developer_tables
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE payment_gateways ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_gateways FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_payment_gateways ON payment_gateways
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

-- ── Indirect via bucket_id -> buckets -> user_id ──
ALTER TABLE files ENABLE ROW LEVEL SECURITY;
ALTER TABLE files FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_files ON files
    USING (bucket_id IN (
        SELECT id FROM buckets WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

-- ── Indirect via file_id -> files -> bucket -> user ──
ALTER TABLE file_shares ENABLE ROW LEVEL SECURITY;
ALTER TABLE file_shares FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_file_shares ON file_shares
    USING (file_id IN (
        SELECT f.id FROM files f JOIN buckets b ON f.bucket_id = b.id
        WHERE b.user_id = current_setting('app.tenant_id', true)::uuid
    ));

-- ── Indirect via api_key_id -> api_keys -> user_id ──
ALTER TABLE api_key_usage_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_key_usage_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_api_key_usage_logs ON api_key_usage_logs
    USING (api_key_id IN (
        SELECT id FROM api_keys WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

-- ── Indirect via table_id -> developer_tables -> user_id ──
ALTER TABLE developer_table_fields ENABLE ROW LEVEL SECURITY;
ALTER TABLE developer_table_fields FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_developer_table_fields ON developer_table_fields
    USING (table_id IN (
        SELECT id FROM developer_tables WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

-- ── Indirect via project_id -> projects -> user_id ──
ALTER TABLE database_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE database_connections FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_database_connections ON database_connections
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

ALTER TABLE project_secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_secrets FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_project_secrets ON project_secrets
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

ALTER TABLE project_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_snapshots FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_project_snapshots ON project_snapshots
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

ALTER TABLE cron_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE cron_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_cron_jobs ON cron_jobs
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

ALTER TABLE waf_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE waf_rules FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_waf_rules ON waf_rules
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

ALTER TABLE kv_store ENABLE ROW LEVEL SECURITY;
ALTER TABLE kv_store FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_kv_store ON kv_store
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

ALTER TABLE analytics_api_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics_api_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_analytics_api_logs ON analytics_api_logs
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

-- ── Indirect via gateway_id -> payment_gateways -> user_id ──
ALTER TABLE payment_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_transactions FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_payment_transactions ON payment_transactions
    USING (gateway_id IN (
        SELECT id FROM payment_gateways WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

-- ── Indirect via job_id -> cron_jobs -> project -> user ──
ALTER TABLE cron_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE cron_executions FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_cron_executions ON cron_executions
    USING (job_id IN (
        SELECT cj.id FROM cron_jobs cj
        JOIN projects p ON cj.project_id = p.id
        WHERE p.user_id = current_setting('app.tenant_id', true)::uuid
    ));

-- ── Collection-based: these cover tenant_users and tenant_sessions ──
-- TenantUsers are project-scoped; the project owner uses app.tenant_id,
-- but tenant_users belong to the project. The policy filters by project_id
-- where the project belongs to the current tenant.
ALTER TABLE tenant_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_users FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_users ON tenant_users
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));

ALTER TABLE tenant_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_sessions ON tenant_sessions
    USING (project_id IN (
        SELECT id FROM projects WHERE user_id = current_setting('app.tenant_id', true)::uuid
    ));
