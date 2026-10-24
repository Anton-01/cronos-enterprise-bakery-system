-- Email template customization (docs/email-template-customization.md §7): administrators edit the content of every
-- transactional email per locale (overrides on top of the classpath defaults), the shared brand and sender, and upload
-- images. The effective template is override ?? default for each (template_key, locale).

CREATE TABLE email_settings (
    tenant_id          UUID PRIMARY KEY,
    brand_name         VARCHAR(80)  NOT NULL,
    primary_color      CHAR(7)      NOT NULL,
    accent_color       CHAR(7)      NOT NULL,
    logo_url           VARCHAR(500),                -- null: the header shows the brand name as text
    header_image_url   VARCHAR(500),
    support_email      VARCHAR(254) NOT NULL,
    address            VARCHAR(200) NOT NULL,
    preferences_url    VARCHAR(500) NOT NULL,
    from_name          VARCHAR(80)  NOT NULL,
    from_address       VARCHAR(254) NOT NULL,
    reply_to           VARCHAR(254),
    revision           BIGINT       NOT NULL DEFAULT 1,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID         NOT NULL
);

CREATE TABLE email_template_override (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    template_key  VARCHAR(80)  NOT NULL,
    locale        VARCHAR(5)   NOT NULL CHECK (locale IN ('es-MX', 'en')),
    subject       VARCHAR(150) NOT NULL,
    preheader     VARCHAR(200) NOT NULL,
    blocks        JSONB        NOT NULL,          -- validated List<EmailBlock>, written by Jackson
    revision      BIGINT       NOT NULL DEFAULT 1,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    UUID         NOT NULL,
    CONSTRAINT uq_email_override UNIQUE (tenant_id, template_key, locale)
);

CREATE TABLE email_template_override_history (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    template_key  VARCHAR(80)  NOT NULL,
    locale        VARCHAR(5)   NOT NULL,
    revision      BIGINT       NOT NULL,
    action        VARCHAR(10)  NOT NULL CHECK (action IN ('SAVED', 'RESET')),
    subject       VARCHAR(150),
    preheader     VARCHAR(200),
    blocks        JSONB,
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    changed_by    UUID         NOT NULL,
    trace_id      VARCHAR(64)
);
CREATE INDEX ix_email_history_lookup ON email_template_override_history (tenant_id, template_key, locale, changed_at DESC);

CREATE TABLE email_asset (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    storage_key   VARCHAR(200) NOT NULL,
    content_type  VARCHAR(40)  NOT NULL,
    size_bytes    INTEGER      NOT NULL,
    width         INTEGER,
    height        INTEGER,
    referenced    BOOLEAN      NOT NULL DEFAULT FALSE,   -- set when a save points to it; cleanup job skips TRUE
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID         NOT NULL,
    CONSTRAINT uq_email_asset UNIQUE (tenant_id, sha256)
);
CREATE INDEX ix_email_asset_cleanup ON email_asset (created_at) WHERE NOT referenced;

-- Editing the templates, the brand and the sender (a phishing-relevant change): platform administrators.
INSERT INTO permissions (name, description, module, resource, action, risk, deprecated, created_at, created_by) VALUES
    ('NOTIFICATION.EMAIL_TEMPLATE.MANAGE', 'NOTIFICATION.EMAIL_TEMPLATE.MANAGE', 'NOTIFICATION', 'EMAIL_TEMPLATE', 'MANAGE', 'HIGH', FALSE, now(), 'FLYWAY_V31')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.name = 'NOTIFICATION.EMAIL_TEMPLATE.MANAGE'
ON CONFLICT DO NOTHING;

-- Tokens carry permissions: force a refresh for everyone whose access just changed.
UPDATE users SET access_version = access_version + 1
WHERE id IN (SELECT ur.user_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.code = 'ADMIN');
