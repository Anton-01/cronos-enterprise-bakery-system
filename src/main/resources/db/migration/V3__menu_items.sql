-- DB-driven navigation catalog. Purely additive (new table only).
-- Seed data below is a REPRESENTATIVE nav tree over existing modules, gated by the 3 permissions
-- that exist today (VIEW_DASHBOARD / MANAGE_USERS / ALL_ACCESS) — it demonstrates the end-to-end
-- mechanism, not a definitive product information architecture. Extend with finer-grained
-- permissions as the product needs them.

CREATE TABLE menu_items (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(60) NOT NULL UNIQUE,
    label_en            VARCHAR(100) NOT NULL,
    label_es            VARCHAR(100) NOT NULL,
    icon                VARCHAR(60),
    path                VARCHAR(200),
    parent_id           BIGINT REFERENCES menu_items(id),
    display_order       INTEGER NOT NULL DEFAULT 0,
    required_permission VARCHAR(100),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_menu_items_parent_id ON menu_items(parent_id);

-- Top-level items
INSERT INTO menu_items (code, label_en, label_es, icon, path, display_order, required_permission) VALUES
    ('dashboard', 'Dashboard', 'Tablero', 'dashboard', '/dashboard', 1, 'VIEW_DASHBOARD'),
    ('catalog', 'Catalog', 'Catálogo', 'inventory', NULL, 2, 'VIEW_DASHBOARD'),
    ('recipes', 'Recipes', 'Recetas', 'restaurant_menu', '/recipes', 3, 'VIEW_DASHBOARD'),
    ('quotes', 'Quotes', 'Cotizaciones', 'request_quote', '/quotes', 4, 'VIEW_DASHBOARD'),
    ('administration', 'Administration', 'Administración', 'admin_panel_settings', NULL, 5, 'MANAGE_USERS');

-- Catalog children
INSERT INTO menu_items (code, label_en, label_es, icon, path, parent_id, display_order, required_permission)
SELECT 'catalog_raw_materials', 'Raw Materials', 'Materias Primas', 'science', '/catalog/raw-materials', id, 1, 'VIEW_DASHBOARD'
FROM menu_items WHERE code = 'catalog'
UNION ALL
SELECT 'catalog_categories', 'Categories', 'Categorías', 'category', '/catalog/categories', id, 2, 'VIEW_DASHBOARD'
FROM menu_items WHERE code = 'catalog'
UNION ALL
SELECT 'catalog_units', 'Units', 'Unidades', 'straighten', '/catalog/units', id, 3, 'VIEW_DASHBOARD'
FROM menu_items WHERE code = 'catalog'
UNION ALL
SELECT 'catalog_allergens', 'Allergens', 'Alérgenos', 'warning', '/catalog/allergens', id, 4, 'VIEW_DASHBOARD'
FROM menu_items WHERE code = 'catalog';

-- Administration children
INSERT INTO menu_items (code, label_en, label_es, icon, path, parent_id, display_order, required_permission)
SELECT 'administration_users', 'Users', 'Usuarios', 'people', '/admin/users', id, 1, 'MANAGE_USERS'
FROM menu_items WHERE code = 'administration'
UNION ALL
SELECT 'administration_roles', 'Roles & Permissions', 'Roles y Permisos', 'security', '/admin/roles', id, 2, 'ALL_ACCESS'
FROM menu_items WHERE code = 'administration';
