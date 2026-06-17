-- V8: Payment Gateway Service
-- Stores user-owned payment gateway credentials (encrypted) and their transaction records

CREATE TABLE IF NOT EXISTS payment_gateways (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id                  UUID         NOT NULL,
    display_name             VARCHAR(120) NOT NULL,
    provider                 VARCHAR(30)  NOT NULL,
    mode                     VARCHAR(10)  NOT NULL DEFAULT 'TEST',
    encrypted_api_key        TEXT,
    encrypted_secret_key     TEXT,
    encrypted_webhook_secret TEXT,
    custom_base_url          TEXT,
    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_gateways PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_payment_gateways_user ON payment_gateways(user_id);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    gateway_id              UUID         NOT NULL,
    amount                  NUMERIC(18,2) NOT NULL,
    currency                VARCHAR(10)  NOT NULL DEFAULT 'USD',
    description             TEXT,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    provider_transaction_id VARCHAR(255),
    provider_response       TEXT,
    error_message           TEXT,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT fk_payment_txn_gateway FOREIGN KEY (gateway_id)
        REFERENCES payment_gateways(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_payment_txn_gateway ON payment_transactions(gateway_id);
CREATE INDEX IF NOT EXISTS idx_payment_txn_status  ON payment_transactions(status);
