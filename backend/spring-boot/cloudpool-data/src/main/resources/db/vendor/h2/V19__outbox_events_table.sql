-- V19: Create outbox_events table for transactional outbox pattern.
--
-- The outbox pattern guarantees reliable event delivery: write the event
-- in the same database transaction as the business operation, then a
-- background relay (OutboxRelay) picks it up and publishes to RabbitMQ.
-- This avoids the dual-write problem (DB commit succeeds but MQ publish
-- fails, or vice versa).

CREATE TABLE IF NOT EXISTS outbox_events (
    event_id           UUID PRIMARY KEY,
    aggregate_type     VARCHAR(255) NOT NULL,
    aggregate_id       VARCHAR(255) NOT NULL,
    event_type         VARCHAR(255) NOT NULL,
    payload            JSON NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')),
    attempt_count      INTEGER NOT NULL DEFAULT 0,
    version            INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_events_status ON outbox_events (status, created_at);
