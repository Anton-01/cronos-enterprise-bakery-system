-- Baking Studio (baking-studio.md §9): pricing method, fixed-cost review, recipe sections, cover card
-- variant and the baker's guide. Additive only (B1); every existing recipe keeps MARKUP (B2).

-- ─── §5 pricing method ──────────────────────────────────────────────────────────────────
ALTER TABLE recipes ADD COLUMN pricing_method VARCHAR(10) NOT NULL DEFAULT 'MARKUP';
ALTER TABLE recipes
    ADD CONSTRAINT ck_recipes_pricing_method CHECK (pricing_method IN ('MARKUP', 'MARGIN')),
    -- MARGIN divides by (1 − p/100): p = 100 would be a division by zero.
    ADD CONSTRAINT ck_recipes_margin_below_100 CHECK (pricing_method = 'MARKUP' OR target_margin_percent < 100);

-- ─── §2 cover: 800 px WebP card variant (list cards and the book view) ─────────────────
ALTER TABLE recipe_files ADD COLUMN card_storage_key VARCHAR(300);

-- ─── §4 fixed costs ─────────────────────────────────────────────────────────────────────
UPDATE user_fixed_costs SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE user_fixed_costs
    ALTER COLUMN is_active SET DEFAULT TRUE,
    ALTER COLUMN is_active SET NOT NULL,
    ALTER COLUMN default_amount TYPE NUMERIC(14, 4),
    ADD COLUMN applies_by_default BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN monthly_amount NUMERIC(14, 4),
    ADD COLUMN monthly_basis NUMERIC(14, 4),
    ADD COLUMN seed_code VARCHAR(40),
    ADD CONSTRAINT ck_user_fixed_costs_monthly_pair CHECK ((monthly_amount IS NULL) = (monthly_basis IS NULL)),
    ADD CONSTRAINT ck_user_fixed_costs_monthly_values CHECK (monthly_amount IS NULL OR (monthly_amount >= 0 AND monthly_basis > 0)),
    ADD CONSTRAINT uq_user_fixed_costs_seed UNIQUE (user_id, seed_code);
CREATE INDEX IF NOT EXISTS ix_user_fixed_costs_user ON user_fixed_costs (user_id, name);

-- PER_UNIT units per batch; NULL keeps the legacy "one per yield unit".
ALTER TABLE recipe_fixed_costs
    ADD COLUMN quantity NUMERIC(14, 4),
    ADD CONSTRAINT ck_recipe_fixed_costs_quantity CHECK (quantity IS NULL OR (quantity >= 0.01 AND quantity <= 100000));

-- ─── §3 recipe sections ─────────────────────────────────────────────────────────────────
-- Section key (§3.2): lower case, accents removed, whitespace collapsed. The only definition of the
-- key: labels store it on write and usageCount groups recipe lines by it.
-- Schema-qualified: PostgreSQL 17+ evaluates index expressions with search_path = pg_catalog, so an
-- unqualified kitchen_fold() is not found when the index below is built over existing rows.
CREATE OR REPLACE FUNCTION kitchen_section_key(value TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT regexp_replace(btrim(public.kitchen_fold(value)), '\s+', ' ', 'g') $$;

CREATE TABLE recipe_sections (
    id            UUID PRIMARY KEY,
    owner_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(40) NOT NULL,
    name_key      VARCHAR(80) NOT NULL, -- unaccent can expand a letter (æ → ae)
    color         CHAR(7),
    display_order INT         NOT NULL DEFAULT 0,
    seed_code     VARCHAR(40),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recipe_sections_name UNIQUE (owner_id, name_key),
    CONSTRAINT ck_recipe_sections_color CHECK (color IS NULL OR color ~ '^#[0-9a-fA-F]{6}$')
);
CREATE INDEX ix_recipe_sections_owner ON recipe_sections (owner_id, display_order);
CREATE INDEX ix_recipe_lines_section_key ON recipe_lines (kitchen_section_key(section)) WHERE section IS NOT NULL;

-- Per-user seeding ledger (B4): a timestamp means "seeded once", deletions are never undone.
CREATE TABLE user_kitchen_settings (
    user_id               UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    sections_seeded_at    TIMESTAMPTZ,
    fixed_costs_seeded_at TIMESTAMPTZ
);

-- ─── §6 baker's guide ───────────────────────────────────────────────────────────────────
CREATE TABLE guide_articles (
    id            UUID PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    category      VARCHAR(20)  NOT NULL,
    title         VARCHAR(120) NOT NULL,
    summary       VARCHAR(300) NOT NULL,
    icon          VARCHAR(40)  NOT NULL,
    tags          JSONB        NOT NULL DEFAULT '[]',
    blocks        JSONB        NOT NULL,
    sources       JSONB        NOT NULL DEFAULT '[]',
    translations  JSONB        NOT NULL DEFAULT '{}',
    display_order INT          NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_guide_articles_category CHECK (category IN ('FOOD_SAFETY', 'TECHNIQUES', 'COSTING')),
    CONSTRAINT ck_guide_articles_code CHECK (code ~ '^[A-Z][A-Z0-9_]{2,49}$')
);

-- owner_id NULL = SYSTEM pan (platform staff); otherwise the user's own.
CREATE TABLE guide_pan_sizes (
    id            UUID PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    owner_id      UUID REFERENCES users (id) ON DELETE CASCADE,
    shape         VARCHAR(12)  NOT NULL,
    name          VARCHAR(80)  NOT NULL,
    diameter_cm   NUMERIC(6, 2),
    length_cm     NUMERIC(6, 2),
    width_cm      NUMERIC(6, 2),
    height_cm     NUMERIC(6, 2) NOT NULL,
    volume_ml     NUMERIC(10, 2),
    servings      INT,
    notes         VARCHAR(200),
    translations  JSONB        NOT NULL DEFAULT '{}',
    display_order INT          NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_guide_pan_sizes_shape CHECK (shape IN ('ROUND', 'SPRINGFORM', 'SQUARE', 'RECTANGULAR', 'SHEET', 'LOAF', 'BUNDT', 'MUFFIN'))
);
CREATE UNIQUE INDEX uq_guide_pan_sizes_user_name ON guide_pan_sizes (owner_id, lower(name)) WHERE owner_id IS NOT NULL;
CREATE INDEX ix_guide_pan_sizes_owner ON guide_pan_sizes (owner_id);

CREATE TABLE guide_conversions (
    code                 VARCHAR(40)   PRIMARY KEY,
    name                 VARCHAR(80)   NOT NULL,
    grams_per_cup        NUMERIC(8, 2) NOT NULL,
    grams_per_tablespoon NUMERIC(8, 2),
    grams_per_teaspoon   NUMERIC(8, 2),
    translations         JSONB         NOT NULL DEFAULT '{}',
    display_order        INT           NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_guide_conversions_cup CHECK (grams_per_cup > 0)
);

CREATE TABLE guide_meta (
    id       SMALLINT PRIMARY KEY DEFAULT 1,
    revision DATE NOT NULL,
    CONSTRAINT ck_guide_meta_single CHECK (id = 1)
);

-- ─── §1 permissions ─────────────────────────────────────────────────────────────────────
-- Rows exist before the startup catalog sync so the role grants below can reference them.
INSERT INTO permissions (name, description, module, resource, action, risk, deprecated, created_at, created_by) VALUES
    ('GUIDE.GUIDE.READ', 'GUIDE.GUIDE.READ', 'GUIDE', 'GUIDE', 'READ', 'LOW', FALSE, now(), 'FLYWAY_V21'),
    ('GUIDE.PAN.MANAGE', 'GUIDE.PAN.MANAGE', 'GUIDE', 'PAN', 'MANAGE', 'LOW', FALSE, now(), 'FLYWAY_V21'),
    ('GUIDE.CONTENT.MANAGE', 'GUIDE.CONTENT.MANAGE', 'GUIDE', 'CONTENT', 'MANAGE', 'MEDIUM', FALSE, now(), 'FLYWAY_V21')
ON CONFLICT (name) DO NOTHING;

-- Everyone who works with recipes reads the guide and keeps their own pans (USER and up).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name IN ('GUIDE.GUIDE.READ', 'GUIDE.PAN.MANAGE')
  AND (r.code IN ('ADMIN', 'MANAGER', 'USER')
       OR r.id IN (SELECT rp.role_id FROM role_permissions rp JOIN permissions x ON x.id = rp.permission_id WHERE x.name = 'RECIPE.RECIPE.READ'))
ON CONFLICT DO NOTHING;

-- Platform staff maintain SYSTEM content (SUPER_ADMIN holds every permission implicitly).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'GUIDE.CONTENT.MANAGE' AND r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Tokens carry permissions: force a refresh for everyone whose access just changed.
UPDATE users SET access_version = access_version + 1
WHERE id IN (SELECT ur.user_id FROM user_roles ur JOIN role_permissions rp ON rp.role_id = ur.role_id
             JOIN permissions p ON p.id = rp.permission_id WHERE p.name LIKE 'GUIDE.%');
