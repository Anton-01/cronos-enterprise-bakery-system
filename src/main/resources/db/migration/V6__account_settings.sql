-- Account Settings module: avatar key + optimistic locking on users, case-insensitive username
-- uniqueness, fiscal (SAT / CFDI 4.0) data, and field-level diffs on the existing audit ledger.

-- 1. users: avatar object key (content-addressed, see AvatarKey) and @Version column.
ALTER TABLE users ADD COLUMN avatar_key VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- The existing UNIQUE(username) is case-sensitive; "Admin" and "admin" must collide too.
-- NOTE: fails loudly if the table already holds case-only duplicates — resolve those by hand first.
CREATE UNIQUE INDEX ux_users_username_lower ON users (lower(username));

-- 2. Fiscal data, 1:1 with users. Short fixed-width SAT codes are VARCHAR + exact-length CHECKs
-- rather than CHAR(n): CHAR blank-pads (a 12-char RFC would come back with a trailing space) and
-- Hibernate's ddl-auto=validate maps String to VARCHAR.
CREATE TABLE user_fiscal_data (
    user_id          UUID         NOT NULL PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    legal_name       VARCHAR(254) NOT NULL,
    tax_id           VARCHAR(13)  NOT NULL,
    taxpayer_type    VARCHAR(12)  NOT NULL,
    tax_regime       VARCHAR(3)   NOT NULL,
    street           VARCHAR(150) NOT NULL,
    exterior_number  VARCHAR(20)  NOT NULL,
    interior_number  VARCHAR(20)  NULL,
    neighborhood     VARCHAR(100) NOT NULL,
    municipality     VARCHAR(100) NOT NULL,
    state            VARCHAR(3)   NOT NULL,
    zip_code         VARCHAR(5)   NOT NULL,
    country          VARCHAR(3)   NOT NULL DEFAULT 'MEX',
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT ck_user_fiscal_data_taxpayer_type CHECK (taxpayer_type IN ('INDIVIDUAL', 'LEGAL_ENTITY')),
    CONSTRAINT ck_user_fiscal_data_tax_id_length CHECK (char_length(tax_id) IN (12, 13)),
    CONSTRAINT ck_user_fiscal_data_tax_regime CHECK (tax_regime ~ '^6[0-9]{2}$'),
    CONSTRAINT ck_user_fiscal_data_state CHECK (char_length(state) = 3),
    CONSTRAINT ck_user_fiscal_data_zip_code CHECK (zip_code ~ '^(0[1-9]|[1-9][0-9])[0-9]{3}$'),
    CONSTRAINT ck_user_fiscal_data_country CHECK (country = 'MEX')
);

-- 3. audit_log (V4) already covers id / occurred_at (created_at) / actor_id (actor_user_id) /
-- action / resource_type (target_type) / resource_id (target_id) / ip / user_agent. Add the two
-- missing pieces rather than a parallel table. ALTER ... ADD COLUMN is not blocked by the
-- row-level immutability trigger.
ALTER TABLE audit_log ADD COLUMN trace_id VARCHAR(64) NULL;
ALTER TABLE audit_log ADD COLUMN changes JSONB NULL;

CREATE INDEX idx_audit_log_trace_id ON audit_log (trace_id);
