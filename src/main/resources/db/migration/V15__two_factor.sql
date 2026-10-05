-- Contract §8.2: self-service TOTP. Secrets are AES-GCM ciphertext (key from KMS, never in the DB).

CREATE TABLE two_factor_enrollments (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    secret_enc   BYTEA       NOT NULL,
    failed_tries SMALLINT    NOT NULL DEFAULT 0,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_two_factor_enrollments_user ON two_factor_enrollments (user_id) WHERE consumed_at IS NULL;

CREATE TABLE user_two_factor (
    user_id        UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret_enc     BYTEA       NOT NULL,
    last_used_step BIGINT,
    enrolled_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_recovery_codes (
    id        BIGSERIAL PRIMARY KEY,
    user_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used_at   TIMESTAMPTZ
);
CREATE INDEX ix_recovery_codes_user ON user_recovery_codes (user_id) WHERE used_at IS NULL;
