-- V21: Add inbox_events table for idempotent event processing with TTL cleanup support.
-- This table was previously created implicitly by Hibernate auto-DDL, which only
-- works for embedded databases (H2). PostgreSQL deployments would miss this table.
-- Now it's explicitly defined with an index on processed_at for the cleanup query.

CREATE TABLE IF NOT EXISTS inbox_events (
    event_id      UUID PRIMARY KEY,
    event_type    VARCHAR(255) NOT NULL,
    processed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inbox_processed_at ON inbox_events (processed_at);
