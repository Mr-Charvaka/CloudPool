-- V15: Enforce Row-Level Security (RLS) for multitenant isolation.
--
-- This migration only runs against PostgreSQL (see db/migration/postgresql
-- vendor-specific Flyway location in application.yml) — H2, used in local/dev,
-- does not implement ROW LEVEL SECURITY or current_setting() the same way.
--
-- Two things had to be true for this to actually do anything, and the
-- original version of this migration got neither right. See
-- FIXES_EXPLAINED.md for the full writeup; summary:
--
--   1. The application must reliably SET LOCAL app.tenant_id at the start
--      of every transaction. This is now done by
--      com.cloudpool.config.TenantAwareDataSourceWrapper, which wraps the
--      DataSource bean so every JPA/JDBC transaction gets it for free.
--      Without this, current_setting('app.tenant_id', true) is always NULL.
--
--   2. FORCE ROW LEVEL SECURITY must be set. This application connects to
--      Postgres using a single configured role (see
--      SPRING_DATASOURCE_USERNAME) which is also the role that runs these
--      Flyway migrations — i.e. it owns these tables. PostgreSQL exempts
--      table owners from RLS policies by default, regardless of how the
--      policy itself is written. Without FORCE, this entire migration is a
--      silent no-op against the role the app actually connects as.
--
-- This version also removes the old "OR current_setting(...) IS NULL"
-- clause. That clause meant: if the GUC is ever unset for any reason
-- (a code path that doesn't go through a transaction, a future bug, a
-- background job that forgot to set tenant context), every row in the
-- table becomes visible to whoever is asking. That is the opposite of
-- defense-in-depth — it makes the database-layer control degrade to "no
-- control" under the exact failure mode it's meant to guard against.
-- Removing it means: no tenant context set => zero rows returned, which
-- is the fail-closed behavior you actually want from RLS. Background jobs
-- or admin tooling that legitimately need to operate across all tenants
-- should use a dedicated role with the BYPASSRLS attribute, not rely on
-- this policy quietly stepping aside.

ALTER TABLE vector_collections ENABLE ROW LEVEL SECURITY;
ALTER TABLE vector_collections FORCE ROW LEVEL SECURITY;

ALTER TABLE vector_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE vector_documents FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_vector_collections
    ON vector_collections
    USING (user_id = current_setting('app.tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_vector_documents
    ON vector_documents
    USING (
        collection_id IN (
            SELECT id FROM vector_collections
            WHERE user_id = current_setting('app.tenant_id', true)::uuid
        )
    );
