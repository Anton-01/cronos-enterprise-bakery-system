-- Authentication rebuild (auth-quotes-calendar-notifications §2, §3, §9). Adapted to this schema:
--  * username/email lookups already use lower(username) (ux_users_username_lower, V6) and email_blind_index
--    (email is encrypted), so no *_normalized columns are added;
--  * the lock end is users.status_until (status LOCKED), the failure counter users.failed_login_attempts;
--  * single-use reset/verification links stay in user_tokens (hashed, V9) instead of two parallel tables;
--  * sign-in attempts keep landing in login_history, now also for unknown identifiers (user_id NULL).

-- ─── users ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN first_failed_at   TIMESTAMPTZ,
    ADD COLUMN lock_count        INT NOT NULL DEFAULT 0,
    ADD COLUMN last_locked_at    TIMESTAMPTZ,
    ADD COLUMN terms_version     VARCHAR(20),
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    DROP CONSTRAINT ck_users_status,
    ADD CONSTRAINT ck_users_status CHECK (status IN ('PENDING_APPROVAL', 'PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'LOCKED', 'DEACTIVATED'));

-- ─── security policy: progressive lockout, screen lock, remember-me, self-registration ──
ALTER TABLE security_policy
    ADD COLUMN lockout_backoff_multiplier SMALLINT NOT NULL DEFAULT 2 CHECK (lockout_backoff_multiplier BETWEEN 1 AND 10),
    ADD COLUMN lockout_max_minutes        INT      NOT NULL DEFAULT 1440 CHECK (lockout_max_minutes BETWEEN 1 AND 43200),
    ADD COLUMN failure_window_minutes     SMALLINT NOT NULL DEFAULT 15 CHECK (failure_window_minutes BETWEEN 1 AND 1440),
    ADD COLUMN screen_lock_minutes        SMALLINT NOT NULL DEFAULT 15 CHECK (screen_lock_minutes BETWEEN 0 AND 480),
    ADD COLUMN remember_me_days           SMALLINT NOT NULL DEFAULT 30 CHECK (remember_me_days BETWEEN 1 AND 365),
    ADD COLUMN require_email_verification BOOLEAN  NOT NULL DEFAULT TRUE,
    ADD COLUMN registration_ttl_days      SMALLINT NOT NULL DEFAULT 30 CHECK (registration_ttl_days BETWEEN 1 AND 365),
    ADD COLUMN disallow_user_info         BOOLEAN  NOT NULL DEFAULT TRUE;

-- ─── sign-in attempts ───────────────────────────────────────────────────────────────────
ALTER TABLE login_history
    ALTER COLUMN user_id DROP NOT NULL,
    ALTER COLUMN outcome TYPE VARCHAR(32),
    ADD COLUMN identifier_hash CHAR(64),
    ADD COLUMN trace_id        VARCHAR(64);
CREATE INDEX ix_login_history_identifier_time ON login_history (identifier_hash, login_at DESC) WHERE identifier_hash IS NOT NULL;
CREATE INDEX ix_login_history_ip_time ON login_history (ip_address, login_at DESC);

-- ─── two-factor challenges (second step of the sign-in) ─────────────────────────────────
CREATE TABLE two_factor_challenges (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    remember_me BOOLEAN     NOT NULL,
    ip_address  VARCHAR(45),
    attempts    INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX ix_two_factor_challenges_expiry ON two_factor_challenges (expires_at);

-- ─── single-use tokens: who asked for them ──────────────────────────────────────────────
ALTER TABLE user_tokens
    ADD COLUMN requested_ip         VARCHAR(45),
    ADD COLUMN requested_user_agent VARCHAR(512);

-- ─── sessions: screen lock and remember-me ──────────────────────────────────────────────
ALTER TABLE user_sessions
    ADD COLUMN locked_at   TIMESTAMPTZ,
    ADD COLUMN remember_me BOOLEAN NOT NULL DEFAULT FALSE;

-- Refresh tokens are now stored as SHA-256 hex digests; existing raw tokens keep working until they expire.
COMMENT ON COLUMN refresh_tokens.token IS 'SHA-256 hex of the opaque refresh token (raw only for tokens issued before V26)';

-- ─── self-registration requests (§3) ────────────────────────────────────────────────────
CREATE SEQUENCE registration_reference_seq;

CREATE TABLE registration_requests (
    id             UUID PRIMARY KEY,
    reference      VARCHAR(20)  NOT NULL UNIQUE,
    user_id        UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    business_name  VARCHAR(120),
    message        VARCHAR(500),
    status         VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(512),
    submitted_at   TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    reviewed_at    TIMESTAMPTZ,
    reviewed_by    UUID REFERENCES users (id),
    reject_reason  VARCHAR(32) CHECK (reject_reason IS NULL OR reject_reason IN
        ('UNKNOWN_APPLICANT', 'DUPLICATE_ACCOUNT', 'INCOMPLETE_INFORMATION', 'POLICY_VIOLATION', 'OTHER')),
    review_comment VARCHAR(500),
    granted_roles  BIGINT[]     NOT NULL DEFAULT '{}',
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_registration_status_time ON registration_requests (status, submitted_at);
CREATE INDEX ix_registration_ip_time ON registration_requests (ip_address, submitted_at);
CREATE INDEX ix_registration_expiry ON registration_requests (expires_at) WHERE status = 'PENDING';
