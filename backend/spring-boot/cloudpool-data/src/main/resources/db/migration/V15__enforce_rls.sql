-- V15: Enforce Row-Level Security (RLS) for multitenant isolation
-- This is a foundational infrastructure update to ensure that when current_tenant is set, 
-- cross-tenant leakage is physically impossible at the Postgres layer.

ALTER TABLE vector_collections ENABLE ROW LEVEL SECURITY;
ALTER TABLE vector_documents ENABLE ROW LEVEL SECURITY;

-- If a session sets 'app.tenant_id', restrict access; otherwise allow for backwards compatibility 
-- with superuser/legacy queries during transition.
CREATE POLICY tenant_isolation_vector_collections 
    ON vector_collections
    USING (
        current_setting('app.tenant_id', true) IS NULL 
        OR user_id = current_setting('app.tenant_id', true)::uuid
    );

CREATE POLICY tenant_isolation_vector_documents 
    ON vector_documents
    USING (
        current_setting('app.tenant_id', true) IS NULL 
        OR collection_id IN (
            SELECT id FROM vector_collections WHERE user_id = current_setting('app.tenant_id', true)::uuid
        )
    );
