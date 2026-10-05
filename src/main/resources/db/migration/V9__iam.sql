-- IAM (spec iam-finance-settings.md §12): user lifecycle, code-defined permission catalog,
-- permission groups, per-user grants/denials, SoD rules, security policy, single-use tokens and
-- the audit ledger extensions. Adapted to this schema: users/roles/permissions/audit_log already
-- exist, timestamps on legacy tables stay TIMESTAMP, created_by/updated_by on legacy tables stay
-- VARCHAR (username) so the new UUID references get their own *_id columns.

CREATE EXTENSION IF NOT EXISTS unaccent;

-- ─── users ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN job_title          VARCHAR(100),
    ADD COLUMN department         VARCHAR(100),
    ADD COLUMN employee_number    VARCHAR(30),
    ADD COLUMN locale             VARCHAR(5)  NOT NULL DEFAULT 'es-MX',
    ADD COLUMN status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN status_reason      VARCHAR(30),
    ADD COLUMN status_comment     VARCHAR(500),
    ADD COLUMN status_until       TIMESTAMPTZ,
    ADD COLUMN status_changed_at  TIMESTAMPTZ,
    ADD COLUMN status_changed_by  UUID REFERENCES users (id),
    ADD COLUMN access_expires_at  DATE,
    ADD COLUMN require_two_factor BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN access_version     BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN created_by_id      UUID REFERENCES users (id),
    ADD COLUMN updated_by_id      UUID REFERENCES users (id),
    ADD CONSTRAINT ck_users_locale CHECK (locale IN ('es-MX', 'en')),
    ADD CONSTRAINT ck_users_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'LOCKED', 'DEACTIVATED')),
    ADD CONSTRAINT ck_users_status_reason CHECK (status_reason IS NULL OR status_reason IN
        ('SECURITY_INCIDENT', 'POLICY_VIOLATION', 'OFFBOARDING', 'LEAVE_OF_ABSENCE', 'ROLE_CHANGE', 'ADMIN_REQUEST', 'OTHER')),
    ADD CONSTRAINT ck_users_employee_number CHECK (employee_number IS NULL OR employee_number ~ '^[A-Za-z0-9-]+$');

-- Backfill the lifecycle from the legacy flags; the flags stay and are kept in sync by the IAM services.
UPDATE users SET status = 'DEACTIVATED', status_reason = 'OTHER', status_comment = 'LEGACY_DISABLED',
                 status_changed_at = now()
WHERE NOT enabled;
UPDATE users SET status = 'LOCKED', status_reason = 'SECURITY_INCIDENT', status_comment = 'LEGACY_LOCKED',
                 status_until = locked_until AT TIME ZONE 'America/Mexico_City', status_changed_at = now()
WHERE enabled AND NOT account_non_locked;

-- Username is already unique case-insensitively (ux_users_username_lower, V6); email via email_blind_index.
CREATE UNIQUE INDEX ux_users_employee_no ON users (upper(employee_number)) WHERE employee_number IS NOT NULL;
CREATE INDEX ix_users_status ON users (status);
CREATE INDEX ix_users_last_login ON users (last_login_at);
CREATE INDEX ix_users_access_expires ON users (access_expires_at) WHERE access_expires_at IS NOT NULL;
CREATE INDEX ix_users_status_until ON users (status_until) WHERE status_until IS NOT NULL;

-- user_roles: who granted the membership and when.
ALTER TABLE user_roles
    ADD COLUMN created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by_id UUID REFERENCES users (id);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- ─── permissions (code-defined; synchronised from PermissionCatalog at startup) ─────────
-- permissions.name is the permission code (MODULE.RESOURCE.ACTION).
ALTER TABLE permissions
    ADD COLUMN module     VARCHAR(40),
    ADD COLUMN risk       VARCHAR(10),
    ADD COLUMN deprecated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_permissions_risk CHECK (risk IS NULL OR risk IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

CREATE TABLE permission_dependencies (
    code       VARCHAR(100) NOT NULL REFERENCES permissions (name),
    depends_on VARCHAR(100) NOT NULL REFERENCES permissions (name),
    PRIMARY KEY (code, depends_on),
    CONSTRAINT ck_permission_dependencies_self CHECK (code <> depends_on)
);

-- ─── roles ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE roles
    ADD COLUMN code          VARCHAR(50),
    ADD COLUMN color         VARCHAR(7),
    ADD COLUMN system        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN status        VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN version       BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN created_by_id UUID REFERENCES users (id),
    ADD COLUMN updated_by_id UUID REFERENCES users (id),
    ADD CONSTRAINT ck_roles_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),
    ADD CONSTRAINT ck_roles_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- Legacy role names were already code-like (SUPER_ADMIN, USER): normalise them into codes.
UPDATE roles
SET code = left(regexp_replace(regexp_replace(upper(regexp_replace(name, '^ROLE_', '', 'i')), '[^A-Z0-9]+', '_', 'g'), '^_+|_+$', '', 'g'), 50);
UPDATE roles SET code = 'ROLE' || id WHERE code IS NULL OR code !~ '^[A-Z][A-Z0-9_]{1,49}$';
ALTER TABLE roles ALTER COLUMN code SET NOT NULL;
ALTER TABLE roles ADD CONSTRAINT ck_roles_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,49}$');
CREATE UNIQUE INDEX ux_roles_code ON roles (code);
-- Legacy writers (/admin/roles) know nothing about codes: derive one from the name on insert.
CREATE OR REPLACE FUNCTION roles_default_code() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.code IS NULL THEN
        NEW.code := left(regexp_replace(regexp_replace(upper(regexp_replace(NEW.name, '^ROLE_', '', 'i')), '[^A-Z0-9]+', '_', 'g'), '^_+|_+$', '', 'g'), 50);
        IF NEW.code !~ '^[A-Z][A-Z0-9_]{1,49}$' OR EXISTS (SELECT 1 FROM roles WHERE code = NEW.code) THEN
            NEW.code := 'CUSTOM_' || upper(substr(md5(random()::text), 1, 10));
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER roles_default_code BEFORE INSERT ON roles FOR EACH ROW EXECUTE FUNCTION roles_default_code();
CREATE UNIQUE INDEX ux_roles_name_ci ON roles (lower(name));
-- Display names ("Gerente de ventas") need more room than the legacy 50 chars.
ALTER TABLE roles ALTER COLUMN name TYPE VARCHAR(100);
ALTER TABLE roles ALTER COLUMN description TYPE VARCHAR(500);

CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

-- ─── permission groups ──────────────────────────────────────────────────────────────────
CREATE TABLE permission_groups (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    system      BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID REFERENCES users (id),
    updated_at  TIMESTAMPTZ,
    updated_by  UUID REFERENCES users (id),
    CONSTRAINT ck_permission_groups_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,49}$'),
    CONSTRAINT ck_permission_groups_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE UNIQUE INDEX ux_permission_groups_name_ci ON permission_groups (lower(name));

CREATE TABLE permission_group_permissions (
    group_id        BIGINT       NOT NULL REFERENCES permission_groups (id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions (name),
    PRIMARY KEY (group_id, permission_code)
);

CREATE TABLE role_permission_groups (
    role_id  BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES permission_groups (id),
    PRIMARY KEY (role_id, group_id)
);
CREATE INDEX ix_role_permission_groups_group ON role_permission_groups (group_id);

CREATE TABLE user_permission_groups (
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    group_id      BIGINT      NOT NULL REFERENCES permission_groups (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by_id UUID REFERENCES users (id),
    PRIMARY KEY (user_id, group_id)
);
CREATE INDEX ix_user_permission_groups_group ON user_permission_groups (group_id);

-- One row per (user, code): a code can never be both granted and denied.
CREATE TABLE user_permission_overrides (
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions (name),
    effect          VARCHAR(5)   NOT NULL CHECK (effect IN ('GRANT', 'DENY')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by_id   UUID REFERENCES users (id),
    PRIMARY KEY (user_id, permission_code)
);

-- ─── segregation of duties ──────────────────────────────────────────────────────────────
CREATE TABLE sod_rules (
    code           VARCHAR(60)  PRIMARY KEY,
    name_es        VARCHAR(150) NOT NULL,
    name_en        VARCHAR(150) NOT NULL,
    description_es VARCHAR(500) NOT NULL,
    description_en VARCHAR(500) NOT NULL,
    severity       VARCHAR(10)  NOT NULL CHECK (severity IN ('WARNING', 'BLOCKING')),
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE sod_rule_sets (
    rule_code       VARCHAR(60)  NOT NULL REFERENCES sod_rules (code) ON DELETE CASCADE,
    set_index       SMALLINT     NOT NULL,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions (name),
    PRIMARY KEY (rule_code, set_index, permission_code)
);

-- ─── single-use tokens: invitations, password resets, email verification ────────────────
CREATE TABLE user_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(20) NOT NULL CHECK (purpose IN ('INVITATION', 'PASSWORD_RESET', 'EMAIL_VERIFICATION')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_user_tokens_user_purpose ON user_tokens (user_id, purpose) WHERE used_at IS NULL;

-- ─── security policy (singleton) ────────────────────────────────────────────────────────
CREATE TABLE security_policy (
    id                         SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    password_min_length        SMALLINT NOT NULL CHECK (password_min_length BETWEEN 8 AND 128),
    password_require_uppercase BOOLEAN  NOT NULL,
    password_require_lowercase BOOLEAN  NOT NULL,
    password_require_digit     BOOLEAN  NOT NULL,
    password_require_symbol    BOOLEAN  NOT NULL,
    password_history           SMALLINT NOT NULL CHECK (password_history BETWEEN 0 AND 24),
    password_max_age_days      SMALLINT NOT NULL CHECK (password_max_age_days BETWEEN 0 AND 365),
    max_failed_attempts        SMALLINT NOT NULL CHECK (max_failed_attempts BETWEEN 3 AND 20),
    lockout_minutes            SMALLINT NOT NULL CHECK (lockout_minutes BETWEEN 1 AND 1440),
    session_idle_minutes       SMALLINT NOT NULL CHECK (session_idle_minutes BETWEEN 5 AND 480),
    session_absolute_hours     SMALLINT NOT NULL CHECK (session_absolute_hours BETWEEN 1 AND 720),
    max_concurrent_sessions    SMALLINT NOT NULL CHECK (max_concurrent_sessions BETWEEN 1 AND 20),
    invitation_ttl_hours       SMALLINT NOT NULL CHECK (invitation_ttl_hours BETWEEN 1 AND 336),
    version                    BIGINT   NOT NULL DEFAULT 0,
    updated_at                 TIMESTAMPTZ,
    updated_by                 UUID REFERENCES users (id),
    CONSTRAINT ck_security_policy_idle CHECK (session_idle_minutes <= session_absolute_hours * 60)
);

CREATE TABLE security_policy_2fa_roles (
    role_id BIGINT PRIMARY KEY REFERENCES roles (id) ON DELETE CASCADE
);

-- ─── sign-in history ────────────────────────────────────────────────────────────────────
ALTER TABLE login_history ADD COLUMN outcome VARCHAR(20);
UPDATE login_history SET outcome = CASE WHEN successful THEN 'SUCCESS' ELSE 'FAILURE' END;
CREATE INDEX ix_login_history_user_time ON login_history (user_id, login_at DESC);
CREATE INDEX ix_user_sessions_user_active ON user_sessions (user_id) WHERE is_active;

-- ─── audit ledger (extends V4 audit_log instead of a parallel table) ────────────────────
-- One ledger for every module: account, catalog and IAM events are queried together by
-- /iam/audit-events. Rows stay immutable (V4 trigger); ADD COLUMN is not blocked by it.
ALTER TABLE audit_log
    ADD COLUMN category     VARCHAR(30),
    ADD COLUMN outcome      VARCHAR(10),
    ADD COLUMN severity     VARCHAR(10),
    ADD COLUMN actor_label  VARCHAR(200),
    ADD COLUMN target_label VARCHAR(200),
    ADD COLUMN params       JSONB,
    ADD COLUMN reason       VARCHAR(500),
    ADD COLUMN prev_hash    VARCHAR(64),
    ADD COLUMN hash         VARCHAR(64),
    ALTER COLUMN action TYPE VARCHAR(60);

-- Historic rows predate categories; classify them once (the trigger only fires per row on
-- UPDATE, so it is disabled for this one backfill and re-enabled immediately).
ALTER TABLE audit_log DISABLE TRIGGER audit_log_immutable;
UPDATE audit_log SET
    category = CASE
        WHEN action IN ('USER_CREATED', 'USER_CREATED_WITH_PROFILE', 'USER_UPDATED') THEN 'USER_ADMINISTRATION'
        WHEN action IN ('USER_ROLES_ASSIGNED', 'ROLE_CREATED', 'ROLE_UPDATED') THEN 'ACCESS_CONTROL'
        WHEN action IN ('USER_LOCKED', 'USER_UNLOCKED', 'USER_FORCE_LOGOUT', 'USER_TWO_FACTOR_DISABLED',
                        'USER_PASSWORD_RESET_INITIATED', 'PASSWORD_CHANGED') THEN 'SECURITY'
        WHEN action LIKE 'DATA_IMPORT_%' THEN 'DATA'
        WHEN action LIKE 'UNIT_TYPE_%' OR action LIKE 'MEASUREMENT_UNIT_%' THEN 'CONFIGURATION'
        ELSE 'USER_ADMINISTRATION' END,
    outcome  = CASE WHEN action IN ('DATA_IMPORT_REJECTED', 'DATA_IMPORT_FAILED') THEN 'FAILURE' ELSE 'SUCCESS' END,
    severity = 'NOTICE';
ALTER TABLE audit_log ENABLE TRIGGER audit_log_immutable;

ALTER TABLE audit_log
    ALTER COLUMN category SET DEFAULT 'USER_ADMINISTRATION',
    ALTER COLUMN category SET NOT NULL,
    ALTER COLUMN outcome SET DEFAULT 'SUCCESS',
    ALTER COLUMN outcome SET NOT NULL,
    ALTER COLUMN severity SET DEFAULT 'NOTICE',
    ALTER COLUMN severity SET NOT NULL,
    ADD CONSTRAINT ck_audit_log_category CHECK (category IN
        ('AUTHENTICATION', 'USER_ADMINISTRATION', 'ACCESS_CONTROL', 'SECURITY', 'CONFIGURATION', 'DATA')),
    ADD CONSTRAINT ck_audit_log_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    ADD CONSTRAINT ck_audit_log_severity CHECK (severity IN ('INFO', 'NOTICE', 'WARNING', 'CRITICAL'));

-- Defence in depth beyond the row trigger: statement-level TRUNCATE is rejected too.
CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_audit_log_modification();

CREATE INDEX ix_audit_log_category_time ON audit_log (category, created_at DESC);
CREATE INDEX ix_audit_log_actor_time ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_log_target_time ON audit_log (target_type, target_id, created_at DESC);
