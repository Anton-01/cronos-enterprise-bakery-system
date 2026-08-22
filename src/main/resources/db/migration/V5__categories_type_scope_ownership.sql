-- Extends the existing `categories` table with the type/scope/ownership model needed for the
-- secure Category Management module: PRODUCT vs INGREDIENT (type), SYSTEM vs USER (scope), and a
-- per-USER-row owner (user_id). Additive + backfilled, not a recreate — `categories` already
-- exists (V1) and may hold data.
--
-- `is_system_default` (boolean) is superseded by `scope` and dropped; `status` moves from the
-- shared RecordStatus domain (ACTIVE/INACTIVE/ARCHIVED) to the category-specific
-- CategoryStatus (ACTIVE/TRASHED) — TRASHED is reached exclusively via soft-delete
-- (CategoryJpaEntity's @SQLDelete), not a manual status change, so INACTIVE/ARCHIVED rows are
-- remapped to ACTIVE rather than TRASHED (nothing about them was ever "deleted").

ALTER TABLE categories ADD COLUMN type VARCHAR(20);
ALTER TABLE categories ADD COLUMN scope VARCHAR(20);
ALTER TABLE categories ADD COLUMN user_id UUID;

UPDATE categories SET type = 'PRODUCT' WHERE type IS NULL;
UPDATE categories SET scope = CASE WHEN is_system_default THEN 'SYSTEM' ELSE 'USER' END WHERE scope IS NULL;
UPDATE categories SET status = 'ACTIVE' WHERE status <> 'ACTIVE';

ALTER TABLE categories ALTER COLUMN type SET NOT NULL;
ALTER TABLE categories ALTER COLUMN scope SET NOT NULL;

ALTER TABLE categories ADD CONSTRAINT chk_categories_type CHECK (type IN ('PRODUCT', 'INGREDIENT'));
ALTER TABLE categories ADD CONSTRAINT chk_categories_scope CHECK (scope IN ('SYSTEM', 'USER'));

-- A SYSTEM row can never have an owner; a USER row must always have one.
ALTER TABLE categories ADD CONSTRAINT chk_categories_scope_user_id CHECK (
    (scope = 'SYSTEM' AND user_id IS NULL) OR (scope = 'USER' AND user_id IS NOT NULL)
);

ALTER TABLE categories ADD CONSTRAINT fk_categories_user_id FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_status_check;
ALTER TABLE categories ADD CONSTRAINT chk_categories_status CHECK (status IN ('ACTIVE', 'TRASHED'));

ALTER TABLE categories DROP COLUMN is_system_default;

-- Uniqueness is per (type, owner) rather than global: two different users — or a user and
-- SYSTEM — may legitimately reuse the same category name. COALESCE onto a fixed sentinel UUID for
-- SYSTEM rows because Postgres treats every NULL as distinct in a unique index (NULL <> NULL), so
-- `UNIQUE (type, user_id, name)` alone would let unlimited duplicate SYSTEM names through.
CREATE UNIQUE INDEX ux_categories_type_owner_name
    ON categories (type, COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name));

CREATE INDEX idx_categories_user_id ON categories (user_id);

-- New error code for CategoryController/Service's IDOR-prevention exception (see
-- UnauthorizedCategoryModificationException / ErrorCodes.UNAUTHORIZED_MODIFICATION).
INSERT INTO catalog_statuses (code, category, http_status) VALUES
    ('UNAUTHORIZED_MODIFICATION', 'SECURITY', 403);

INSERT INTO custom_error_responses (error_code, status_id, title_en, title_es, description_en, description_es)
VALUES (
    'UNAUTHORIZED_MODIFICATION',
    (SELECT id FROM catalog_statuses WHERE code = 'UNAUTHORIZED_MODIFICATION'),
    'Not authorized',
    'No autorizado',
    'You do not have permission to modify this resource — it belongs to another user.',
    'No tienes permiso para modificar este recurso — pertenece a otro usuario.'
);
