-- V20: Create refresh_tokens table for refresh token rotation.
--
-- Implements refresh token rotation with reuse detection:
--   - Each refresh token has a family_id; a new token issued from the
--     same family invalidates the previous one.
--   - If a used/rotated token is presented again, ALL tokens in that
--     family are revoked (reuse detection = token theft indicator).

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash        VARCHAR(128) NOT NULL UNIQUE,
    family_id         UUID NOT NULL,
    expires_at        TIMESTAMP NOT NULL,
    revoked           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at) WHERE revoked = FALSE;
