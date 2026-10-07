-- Kitchen master catalog (kitchen-catalog-and-recipes.md §1, §7, §10.1–10.4). SYSTEM rows only
-- (owner NULL); every statement is idempotent so a re-run inserts only what is missing.
-- Reference prices: MXN, CDMX retail/mayoreo, Oct-2026 (supplier 'Referencia Cronos').

-- ─── permissions (§1) ───────────────────────────────────────────────────────────────────
INSERT INTO permissions (name, description, module, resource, action, risk, deprecated, created_at, created_by) VALUES
    ('CATALOG.INGREDIENT.READ', 'CATALOG.INGREDIENT.READ', 'CATALOG', 'INGREDIENT', 'READ', 'LOW', FALSE, now(), 'FLYWAY_V18'),
    ('CATALOG.INGREDIENT.MANAGE', 'CATALOG.INGREDIENT.MANAGE', 'CATALOG', 'INGREDIENT', 'MANAGE', 'HIGH', FALSE, now(), 'FLYWAY_V18')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'CATALOG.INGREDIENT.READ'
  AND (r.code IN ('ADMIN', 'MANAGER', 'USER')
       OR r.id IN (SELECT rp.role_id FROM role_permissions rp JOIN permissions x ON x.id = rp.permission_id WHERE x.name = 'CATALOG.CATEGORY.READ'))
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'CATALOG.INGREDIENT.MANAGE' AND r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Tokens carry permissions: force a refresh for everyone whose access just changed.
UPDATE users SET access_version = access_version + 1
WHERE id IN (SELECT ur.user_id FROM user_roles ur JOIN role_permissions rp ON rp.role_id = ur.role_id
             JOIN permissions p ON p.id = rp.permission_id WHERE p.name IN ('CATALOG.INGREDIENT.READ', 'CATALOG.INGREDIENT.MANAGE'));

-- ─── units (§7) ─────────────────────────────────────────────────────────────────────────
-- factor = size in the dimension's canonical unit (g / ml / pz). Existing catalogs whose base
-- unit is another known unit get multipliers relative to it; an unknown base aborts.
CREATE TEMP TABLE seed_units (dim TEXT, code TEXT, name TEXT, plural TEXT, factor NUMERIC, canonical BOOLEAN) ON COMMIT DROP;
INSERT INTO seed_units VALUES
    ('MASS', 'g', 'Gramo', 'Gramos', 1, TRUE),
    ('MASS', 'kg', 'Kilogramo', 'Kilogramos', 1000, FALSE),
    ('MASS', 'mg', 'Miligramo', 'Miligramos', 0.001, FALSE),
    ('MASS', 'oz', 'Onza', 'Onzas', 28.3495, FALSE),
    ('MASS', 'lb', 'Libra', 'Libras', 453.592, FALSE),
    ('VOLUME', 'ml', 'Mililitro', 'Mililitros', 1, TRUE),
    ('VOLUME', 'L', 'Litro', 'Litros', 1000, FALSE),
    ('VOLUME', 'taza', 'Taza', 'Tazas', 240, FALSE),
    ('VOLUME', 'cda', 'Cucharada', 'Cucharadas', 15, FALSE),
    ('VOLUME', 'cdta', 'Cucharadita', 'Cucharaditas', 5, FALSE),
    ('VOLUME', 'fl_oz', 'Onza líquida', 'Onzas líquidas', 29.5735, FALSE),
    ('COUNT', 'pz', 'Pieza', 'Piezas', 1, TRUE),
    ('COUNT', 'docena', 'Docena', 'Docenas', 12, FALSE);

CREATE TEMP TABLE seed_unit_aliases (code TEXT, factor NUMERIC) ON COMMIT DROP;
INSERT INTO seed_unit_aliases VALUES
    ('g', 1), ('gr', 1), ('grs', 1), ('gramo', 1), ('kg', 1000), ('kilo', 1000), ('kilogramo', 1000), ('mg', 0.001),
    ('lb', 453.592), ('oz', 28.3495), ('ml', 1), ('mililitro', 1), ('l', 1000), ('lt', 1000), ('litro', 1000),
    ('pz', 1), ('pza', 1), ('pieza', 1), ('u', 1), ('un', 1), ('unidad', 1), ('docena', 12);

DO $$
DECLARE
    u          RECORD;
    type_id    BIGINT;
    base_code  TEXT;
    base_name  TEXT;
    base_f     NUMERIC;
    unit_name  TEXT;
BEGIN
    FOR u IN SELECT * FROM seed_units ORDER BY canonical DESC, dim, factor LOOP
        SELECT id INTO type_id FROM unit_types WHERE dimension = u.dim AND deleted_at IS NULL;
        IF type_id IS NULL THEN
            INSERT INTO unit_types (code_identity, name, dimension, status, created_at, created_by)
            VALUES (CASE u.dim WHEN 'MASS' THEN 'MASS' WHEN 'VOLUME' THEN 'VOLUME' ELSE 'COUNT' END,
                    CASE u.dim WHEN 'MASS' THEN 'Masa' WHEN 'VOLUME' THEN 'Volumen' ELSE 'Conteo' END,
                    u.dim, 'ACTIVE', now(), 'FLYWAY_V18')
            RETURNING id INTO type_id;
        END IF;

        CONTINUE WHEN EXISTS (SELECT 1 FROM measurement_units WHERE code_identity = u.code AND deleted_at IS NULL);

        unit_name := u.name;
        IF EXISTS (SELECT 1 FROM measurement_units WHERE lower(name) = lower(unit_name) AND deleted_at IS NULL) THEN
            unit_name := u.name || ' (' || u.code || ')';
        END IF;

        SELECT code_identity, name INTO base_code, base_name
        FROM measurement_units WHERE unit_type_id = type_id AND is_base_unit AND deleted_at IS NULL;

        IF base_code IS NULL THEN
            INSERT INTO measurement_units (code_identity, name, name_plural, unit_type_id, multiplier_to_base, is_base_unit,
                                           is_system_default, status, created_at, created_by)
            VALUES (u.code, unit_name, u.plural, type_id, u.factor, u.canonical, TRUE, 'ACTIVE', now(), 'FLYWAY_V18');
        ELSE
            SELECT factor INTO base_f FROM seed_unit_aliases
            WHERE code = lower(base_code) OR code = lower(base_name) OR code = kitchen_fold(base_name) LIMIT 1;
            IF base_f IS NULL THEN
                RAISE EXCEPTION 'V18: base unit "%" of dimension % is not a known unit; add unit "%" by hand and re-run.', base_code, u.dim, u.code;
            END IF;
            INSERT INTO measurement_units (code_identity, name, name_plural, unit_type_id, multiplier_to_base, is_base_unit,
                                           is_system_default, status, created_at, created_by)
            VALUES (u.code, unit_name, u.plural, type_id, round(u.factor / base_f, 10), FALSE, TRUE, 'ACTIVE', now(), 'FLYWAY_V18');
        END IF;
    END LOOP;
END $$;

-- ─── categories (§10.2) ─────────────────────────────────────────────────────────────────
CREATE TEMP TABLE seed_categories (type TEXT, code TEXT, name TEXT, sort INT) ON COMMIT DROP;
INSERT INTO seed_categories VALUES
    ('INGREDIENT', 'FLOURS', 'Harinas y almidones', 1), ('INGREDIENT', 'SUGARS', 'Azúcares y endulzantes', 2),
    ('INGREDIENT', 'DAIRY', 'Lácteos', 3), ('INGREDIENT', 'EGGS', 'Huevo', 4), ('INGREDIENT', 'FATS', 'Grasas y aceites', 5),
    ('INGREDIENT', 'LEAVENING', 'Leudantes', 6), ('INGREDIENT', 'CHOCOLATE', 'Chocolate y cacao', 7),
    ('INGREDIENT', 'NUTS_SEEDS', 'Frutos secos y semillas', 8), ('INGREDIENT', 'FRUITS', 'Frutas', 9),
    ('INGREDIENT', 'FLAVORINGS', 'Esencias y saborizantes', 10), ('INGREDIENT', 'SPICES', 'Especias', 11),
    ('INGREDIENT', 'THICKENERS', 'Espesantes y gelificantes', 12), ('INGREDIENT', 'DECORATION', 'Decoración', 13),
    ('INGREDIENT', 'COLORANTS', 'Colorantes', 14), ('INGREDIENT', 'PACKAGING', 'Empaque', 15), ('INGREDIENT', 'OTHER', 'Otros', 16),
    ('PRODUCT', 'CAKES', 'Pasteles', 1), ('PRODUCT', 'CUPCAKES', 'Cupcakes y panquelitos', 2), ('PRODUCT', 'COOKIES', 'Galletas', 3),
    ('PRODUCT', 'BREADS', 'Panes', 4), ('PRODUCT', 'PIES_TARTS', 'Pays y tartas', 5),
    ('PRODUCT', 'INDIVIDUAL_DESSERTS', 'Postres individuales', 6), ('PRODUCT', 'GELATINS', 'Gelatinas y flanes', 7),
    ('PRODUCT', 'CHEESECAKES', 'Cheesecakes', 8), ('PRODUCT', 'PASTRIES', 'Repostería fina', 9), ('PRODUCT', 'SEASONAL', 'Temporada', 10);

-- Existing SYSTEM rows with the same name adopt the code; the rest are inserted.
UPDATE categories c SET code = s.code, status = 'ACTIVE', deleted_at = NULL
FROM seed_categories s
WHERE c.scope = 'SYSTEM' AND c.type = s.type AND c.code IS NULL AND lower(c.name) = lower(s.name)
  AND NOT EXISTS (SELECT 1 FROM categories o WHERE o.scope = 'SYSTEM' AND o.type = s.type AND o.code = s.code);

INSERT INTO categories (name, type, scope, status, created_at, created_by, version, code)
SELECT s.name, s.type, 'SYSTEM', 'ACTIVE', now(), 'FLYWAY_V18', 0, s.code
FROM seed_categories s
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.scope = 'SYSTEM' AND c.type = s.type AND c.code = s.code)
ORDER BY s.type, s.sort;

-- ─── allergens (§10.1) ──────────────────────────────────────────────────────────────────
CREATE TEMP TABLE seed_allergens (code TEXT, name_es TEXT, name_en TEXT, icon TEXT, kw_es TEXT[], kw_en TEXT[]) ON COMMIT DROP;
INSERT INTO seed_allergens VALUES
    ('GLUTEN', 'Cereales con gluten', 'Cereals containing gluten', 'pi pi-sun',
     ARRAY['trigo', 'harina de trigo', 'harina', 'cebada', 'centeno', 'avena', 'espelta', 'kamut', 'semola', 'salvado', 'malta', 'galleta', 'pan molido', 'pan', 'pasta'],
     ARRAY['wheat', 'flour', 'barley', 'rye', 'oat', 'oats', 'spelt', 'semolina', 'malt', 'breadcrumbs', 'cookie', 'biscuit']),
    ('CRUSTACEANS', 'Crustáceos', 'Crustaceans', 'pi pi-flag',
     ARRAY['camaron', 'langosta', 'cangrejo', 'jaiba', 'langostino'], ARRAY['shrimp', 'prawn', 'lobster', 'crab']),
    ('EGG', 'Huevo', 'Egg', 'pi pi-circle-fill',
     ARRAY['huevo', 'huevos', 'clara', 'yema', 'merengue', 'albumina'], ARRAY['egg', 'eggs', 'egg white', 'yolk', 'meringue', 'albumen']),
    ('FISH', 'Pescado', 'Fish', 'pi pi-flag',
     ARRAY['pescado', 'atun', 'salmon', 'anchoa', 'bacalao'], ARRAY['fish', 'tuna', 'salmon', 'anchovy', 'cod']),
    ('PEANUT', 'Cacahuate', 'Peanut', 'pi pi-circle',
     ARRAY['cacahuate', 'cacahuete', 'mani', 'crema de cacahuate'], ARRAY['peanut', 'peanuts', 'peanut butter', 'groundnut']),
    ('SOY', 'Soya', 'Soy', 'pi pi-tag',
     ARRAY['soya', 'soja', 'lecitina de soya', 'tofu'], ARRAY['soy', 'soya', 'soy lecithin', 'tofu']),
    ('MILK', 'Leche y derivados', 'Milk', 'pi pi-heart',
     ARRAY['leche', 'mantequilla', 'crema', 'queso', 'yogur', 'nata', 'suero de leche', 'lactosa', 'caseina', 'leche condensada', 'lechera', 'leche evaporada', 'requeson', 'ghee'],
     ARRAY['milk', 'butter', 'cream', 'cheese', 'yogurt', 'whey', 'lactose', 'casein', 'condensed milk', 'evaporated milk', 'buttermilk']),
    ('TREE_NUTS', 'Frutos de cáscara', 'Tree nuts', 'pi pi-star',
     ARRAY['nuez', 'nueces', 'almendra', 'avellana', 'pistache', 'pistacho', 'nuez de la india', 'macadamia', 'pecana', 'pecan'],
     ARRAY['walnut', 'almond', 'hazelnut', 'pistachio', 'cashew', 'macadamia', 'pecan', 'praline']),
    ('CELERY', 'Apio', 'Celery', 'pi pi-bolt', ARRAY['apio'], ARRAY['celery']),
    ('MUSTARD', 'Mostaza', 'Mustard', 'pi pi-bolt', ARRAY['mostaza'], ARRAY['mustard']),
    ('SESAME', 'Ajonjolí', 'Sesame', 'pi pi-circle', ARRAY['ajonjoli', 'sesamo', 'tahini'], ARRAY['sesame', 'tahini']),
    ('SULPHITES', 'Sulfitos', 'Sulphites', 'pi pi-exclamation-triangle',
     ARRAY['sulfito', 'sulfitos', 'metabisulfito', 'vino'], ARRAY['sulphite', 'sulfite', 'metabisulfite', 'wine']),
    ('LUPIN', 'Altramuz', 'Lupin', 'pi pi-circle', ARRAY['altramuz', 'lupino'], ARRAY['lupin']),
    ('MOLLUSCS', 'Moluscos', 'Molluscs', 'pi pi-flag',
     ARRAY['almeja', 'mejillon', 'ostion', 'pulpo', 'calamar'], ARRAY['clam', 'mussel', 'oyster', 'octopus', 'squid']);

INSERT INTO allergens (code, owner_id, icon, regulations, status)
SELECT code, NULL, icon, ARRAY['NOM-051', 'EU-1169', 'FDA-FALCPA']::varchar(20)[], 'ACTIVE' FROM seed_allergens
ON CONFLICT (owner_key, code) DO NOTHING;

INSERT INTO allergen_i18n (allergen_id, locale, name)
SELECT a.id, l.locale, CASE l.locale WHEN 'es' THEN s.name_es ELSE s.name_en END
FROM seed_allergens s JOIN allergens a ON a.code = s.code AND a.owner_id IS NULL
CROSS JOIN (VALUES ('es'), ('en')) l(locale)
ON CONFLICT (allergen_id, locale) DO NOTHING;

INSERT INTO allergen_keywords (allergen_id, owner_id, locale, keyword)
SELECT a.id, NULL::uuid, 'es', k FROM seed_allergens s JOIN allergens a ON a.code = s.code AND a.owner_id IS NULL, unnest(s.kw_es) k
UNION ALL
SELECT a.id, NULL::uuid, 'en', k FROM seed_allergens s JOIN allergens a ON a.code = s.code AND a.owner_id IS NULL, unnest(s.kw_en) k
ON CONFLICT DO NOTHING;

-- ─── ingredients (§10.3) ────────────────────────────────────────────────────────────────
CREATE TEMP TABLE seed_ingredients (
    code TEXT, category TEXT, dim TEXT, yield_percent NUMERIC, density NUMERIC, allergen_codes TEXT[],
    price NUMERIC, purchase_quantity NUMERIC, unit_code TEXT, name_es TEXT, name_en TEXT) ON COMMIT DROP;
INSERT INTO seed_ingredients VALUES
    ('WHEAT_FLOUR_AP', 'FLOURS', 'MASS', 98, 0.53, ARRAY['GLUTEN']::text[], 26, 1, 'kg', 'Harina de trigo multiusos', 'All-purpose wheat flour'),
    ('CAKE_FLOUR', 'FLOURS', 'MASS', 98, 0.50, ARRAY['GLUTEN']::text[], 38, 1, 'kg', 'Harina de trigo para pastel', 'Cake flour'),
    ('BREAD_FLOUR', 'FLOURS', 'MASS', 98, 0.55, ARRAY['GLUTEN']::text[], 34, 1, 'kg', 'Harina de fuerza para pan', 'Bread flour'),
    ('WHOLE_WHEAT_FLOUR', 'FLOURS', 'MASS', 98, 0.51, ARRAY['GLUTEN']::text[], 42, 1, 'kg', 'Harina integral de trigo', 'Whole wheat flour'),
    ('ALMOND_FLOUR', 'FLOURS', 'MASS', 99, 0.41, ARRAY['TREE_NUTS']::text[], 320, 1, 'kg', 'Harina de almendra', 'Almond flour'),
    ('RICE_FLOUR', 'FLOURS', 'MASS', 99, 0.62, '{}'::text[], 48, 1, 'kg', 'Harina de arroz', 'Rice flour'),
    ('CORNSTARCH', 'FLOURS', 'MASS', 100, 0.54, '{}'::text[], 52, 1, 'kg', 'Fécula de maíz', 'Cornstarch'),
    ('OAT_FLOUR', 'FLOURS', 'MASS', 98, 0.39, ARRAY['GLUTEN']::text[], 70, 1, 'kg', 'Harina de avena', 'Oat flour'),
    ('OATS_ROLLED', 'FLOURS', 'MASS', 100, 0.38, ARRAY['GLUTEN']::text[], 45, 1, 'kg', 'Avena en hojuelas', 'Rolled oats'),
    ('GF_FLOUR_BLEND', 'FLOURS', 'MASS', 98, 0.55, '{}'::text[], 140, 1, 'kg', 'Harina sin gluten (mezcla)', 'Gluten-free flour blend'),
    ('COCONUT_FLOUR', 'FLOURS', 'MASS', 99, 0.47, '{}'::text[], 180, 1, 'kg', 'Harina de coco', 'Coconut flour'),
    ('SEMOLINA', 'FLOURS', 'MASS', 100, 0.67, ARRAY['GLUTEN']::text[], 45, 1, 'kg', 'Sémola de trigo', 'Wheat semolina'),
    ('SUGAR_WHITE', 'SUGARS', 'MASS', 100, 0.85, '{}'::text[], 32, 1, 'kg', 'Azúcar refinada', 'White sugar'),
    ('SUGAR_BROWN', 'SUGARS', 'MASS', 100, 0.83, '{}'::text[], 30, 1, 'kg', 'Azúcar morena', 'Brown sugar'),
    ('SUGAR_POWDERED', 'SUGARS', 'MASS', 98, 0.56, '{}'::text[], 45, 1, 'kg', 'Azúcar glass', 'Powdered sugar'),
    ('SUGAR_MUSCOVADO', 'SUGARS', 'MASS', 100, 0.80, '{}'::text[], 55, 1, 'kg', 'Azúcar mascabado', 'Muscovado sugar'),
    ('HONEY', 'SUGARS', 'VOLUME', 98, 1.42, '{}'::text[], 120, 500, 'ml', 'Miel de abeja', 'Honey'),
    ('MAPLE_SYRUP', 'SUGARS', 'VOLUME', 98, 1.32, '{}'::text[], 230, 500, 'ml', 'Jarabe de maple', 'Maple syrup'),
    ('CORN_SYRUP', 'SUGARS', 'VOLUME', 98, 1.38, '{}'::text[], 68, 500, 'ml', 'Jarabe de maíz', 'Corn syrup'),
    ('PILONCILLO', 'SUGARS', 'MASS', 98, NULL, '{}'::text[], 48, 1, 'kg', 'Piloncillo', 'Piloncillo (unrefined cane sugar)'),
    ('STEVIA', 'SUGARS', 'MASS', 100, 0.60, '{}'::text[], 160, 500, 'g', 'Stevia granulada', 'Granulated stevia'),
    ('ERYTHRITOL', 'SUGARS', 'MASS', 100, 0.85, '{}'::text[], 220, 1, 'kg', 'Eritritol', 'Erythritol'),
    ('CONDENSED_MILK', 'SUGARS', 'VOLUME', 97, 1.30, ARRAY['MILK']::text[], 29, 300, 'ml', 'Leche condensada', 'Sweetened condensed milk'),
    ('DULCE_DE_LECHE', 'SUGARS', 'MASS', 97, NULL, ARRAY['MILK']::text[], 62, 660, 'g', 'Cajeta / dulce de leche', 'Dulce de leche / cajeta'),
    ('MILK_WHOLE', 'DAIRY', 'VOLUME', 100, 1.03, ARRAY['MILK']::text[], 28, 1, 'L', 'Leche entera', 'Whole milk'),
    ('MILK_EVAPORATED', 'DAIRY', 'VOLUME', 98, 1.07, ARRAY['MILK']::text[], 22, 360, 'ml', 'Leche evaporada', 'Evaporated milk'),
    ('MILK_POWDER', 'DAIRY', 'MASS', 100, 0.50, ARRAY['MILK']::text[], 140, 1, 'kg', 'Leche en polvo', 'Milk powder'),
    ('BUTTERMILK', 'DAIRY', 'VOLUME', 100, 1.03, ARRAY['MILK']::text[], 38, 1, 'L', 'Suero de leche (buttermilk)', 'Buttermilk'),
    ('HEAVY_CREAM', 'DAIRY', 'VOLUME', 98, 0.99, ARRAY['MILK']::text[], 105, 1, 'L', 'Crema para batir 35 %', 'Heavy whipping cream 35 %'),
    ('SOUR_CREAM', 'DAIRY', 'MASS', 97, 1.01, ARRAY['MILK']::text[], 42, 450, 'g', 'Crema ácida', 'Sour cream'),
    ('BUTTER_UNSALTED', 'DAIRY', 'MASS', 99, 0.91, ARRAY['MILK']::text[], 190, 1, 'kg', 'Mantequilla sin sal', 'Unsalted butter'),
    ('BUTTER_SALTED', 'DAIRY', 'MASS', 99, 0.91, ARRAY['MILK']::text[], 180, 1, 'kg', 'Mantequilla con sal', 'Salted butter'),
    ('CREAM_CHEESE', 'DAIRY', 'MASS', 98, 1.00, ARRAY['MILK']::text[], 165, 1, 'kg', 'Queso crema', 'Cream cheese'),
    ('MASCARPONE', 'DAIRY', 'MASS', 98, NULL, ARRAY['MILK']::text[], 320, 500, 'g', 'Queso mascarpone', 'Mascarpone cheese'),
    ('RICOTTA', 'DAIRY', 'MASS', 98, NULL, ARRAY['MILK']::text[], 95, 500, 'g', 'Queso ricotta', 'Ricotta cheese'),
    ('YOGURT_GREEK', 'DAIRY', 'MASS', 98, 1.05, ARRAY['MILK']::text[], 85, 1, 'kg', 'Yogur griego natural', 'Plain Greek yogurt'),
    ('MEDIA_CREMA', 'DAIRY', 'VOLUME', 97, 1.00, ARRAY['MILK']::text[], 16, 225, 'ml', 'Media crema', 'Table cream (media crema)'),
    ('COCONUT_MILK', 'DAIRY', 'VOLUME', 98, 0.97, '{}'::text[], 45, 400, 'ml', 'Leche de coco', 'Coconut milk'),
    ('ALMOND_MILK', 'DAIRY', 'VOLUME', 100, 1.02, ARRAY['TREE_NUTS']::text[], 55, 1, 'L', 'Bebida de almendra', 'Almond drink'),
    ('OAT_MILK', 'DAIRY', 'VOLUME', 100, 1.03, ARRAY['GLUTEN']::text[], 52, 1, 'L', 'Bebida de avena', 'Oat drink'),
    ('EGG_WHOLE', 'EGGS', 'COUNT', 89, NULL, ARRAY['EGG']::text[], 85, 30, 'pz', 'Huevo entero (pieza ~50 g)', 'Whole egg (~50 g piece)'),
    ('EGG_WHITE', 'EGGS', 'VOLUME', 100, 1.04, ARRAY['EGG']::text[], 75, 1, 'L', 'Clara de huevo pasteurizada', 'Pasteurised egg white'),
    ('EGG_YOLK', 'EGGS', 'COUNT', 100, NULL, ARRAY['EGG']::text[], 85, 30, 'pz', 'Yema de huevo', 'Egg yolk'),
    ('AQUAFABA', 'EGGS', 'VOLUME', 100, 1.00, '{}'::text[], 20, 400, 'ml', 'Aquafaba (agua de garbanzo)', 'Aquafaba (chickpea water)'),
    ('FLAX_EGG', 'EGGS', 'MASS', 100, 0.45, '{}'::text[], 70, 500, 'g', 'Linaza molida (sustituto de huevo)', 'Ground flaxseed (egg substitute)'),
    ('VEG_SHORTENING', 'FATS', 'MASS', 100, 0.86, '{}'::text[], 60, 1, 'kg', 'Manteca vegetal', 'Vegetable shortening'),
    ('MARGARINE', 'FATS', 'MASS', 100, 0.91, ARRAY['SOY']::text[], 75, 1, 'kg', 'Margarina vegetal', 'Vegetable margarine'),
    ('OIL_CANOLA', 'FATS', 'VOLUME', 100, 0.92, '{}'::text[], 52, 1, 'L', 'Aceite de canola', 'Canola oil'),
    ('OIL_COCONUT', 'FATS', 'VOLUME', 98, 0.92, '{}'::text[], 160, 1, 'L', 'Aceite de coco', 'Coconut oil'),
    ('OIL_OLIVE', 'FATS', 'VOLUME', 100, 0.91, '{}'::text[], 190, 1, 'L', 'Aceite de oliva', 'Olive oil'),
    ('VEGAN_BUTTER', 'FATS', 'MASS', 99, 0.92, '{}'::text[], 180, 500, 'g', 'Mantequilla vegetal (sin lácteos)', 'Plant-based butter (dairy-free)'),
    ('BAKING_POWDER', 'LEAVENING', 'MASS', 100, 0.90, '{}'::text[], 48, 220, 'g', 'Polvo para hornear', 'Baking powder'),
    ('BAKING_SODA', 'LEAVENING', 'MASS', 100, 1.10, '{}'::text[], 18, 200, 'g', 'Bicarbonato de sodio', 'Baking soda'),
    ('YEAST_DRY', 'LEAVENING', 'MASS', 100, 0.65, '{}'::text[], 22, 11, 'g', 'Levadura seca instantánea', 'Instant dry yeast'),
    ('YEAST_FRESH', 'LEAVENING', 'MASS', 100, NULL, '{}'::text[], 35, 500, 'g', 'Levadura fresca', 'Fresh yeast'),
    ('CREAM_OF_TARTAR', 'LEAVENING', 'MASS', 100, 0.95, ARRAY['SULPHITES']::text[], 60, 100, 'g', 'Cremor tártaro', 'Cream of tartar'),
    ('CHOC_DARK_70', 'CHOCOLATE', 'MASS', 98, NULL, ARRAY['SOY', 'MILK']::text[], 420, 1, 'kg', 'Chocolate amargo 70 %', 'Dark chocolate 70 %'),
    ('CHOC_SEMISWEET', 'CHOCOLATE', 'MASS', 98, NULL, ARRAY['SOY', 'MILK']::text[], 320, 1, 'kg', 'Chocolate semiamargo', 'Semisweet chocolate'),
    ('CHOC_MILK', 'CHOCOLATE', 'MASS', 98, NULL, ARRAY['MILK', 'SOY']::text[], 330, 1, 'kg', 'Chocolate de leche', 'Milk chocolate'),
    ('CHOC_WHITE', 'CHOCOLATE', 'MASS', 98, NULL, ARRAY['MILK', 'SOY']::text[], 360, 1, 'kg', 'Chocolate blanco', 'White chocolate'),
    ('CHOC_CHIPS', 'CHOCOLATE', 'MASS', 100, 0.60, ARRAY['MILK', 'SOY']::text[], 190, 1, 'kg', 'Chispas de chocolate', 'Chocolate chips'),
    ('COCOA_POWDER', 'CHOCOLATE', 'MASS', 100, 0.42, '{}'::text[], 240, 1, 'kg', 'Cacao en polvo sin azúcar', 'Unsweetened cocoa powder'),
    ('CHOC_MEXICAN', 'CHOCOLATE', 'MASS', 100, NULL, '{}'::text[], 95, 540, 'g', 'Chocolate de mesa', 'Mexican table chocolate'),
    ('COMPOUND_COATING', 'CHOCOLATE', 'MASS', 98, NULL, ARRAY['MILK', 'SOY']::text[], 140, 1, 'kg', 'Cobertura sabor chocolate', 'Chocolate-flavoured compound coating'),
    ('WALNUT', 'NUTS_SEEDS', 'MASS', 100, 0.42, ARRAY['TREE_NUTS']::text[], 380, 1, 'kg', 'Nuez de Castilla', 'Walnut'),
    ('PECAN', 'NUTS_SEEDS', 'MASS', 100, 0.42, ARRAY['TREE_NUTS']::text[], 420, 1, 'kg', 'Nuez pecana', 'Pecan'),
    ('ALMOND', 'NUTS_SEEDS', 'MASS', 100, 0.60, ARRAY['TREE_NUTS']::text[], 290, 1, 'kg', 'Almendra entera', 'Whole almond'),
    ('ALMOND_SLICED', 'NUTS_SEEDS', 'MASS', 100, 0.38, ARRAY['TREE_NUTS']::text[], 340, 1, 'kg', 'Almendra fileteada', 'Sliced almonds'),
    ('HAZELNUT', 'NUTS_SEEDS', 'MASS', 100, 0.57, ARRAY['TREE_NUTS']::text[], 480, 1, 'kg', 'Avellana', 'Hazelnut'),
    ('PISTACHIO', 'NUTS_SEEDS', 'MASS', 100, 0.52, ARRAY['TREE_NUTS']::text[], 690, 1, 'kg', 'Pistache sin cáscara', 'Shelled pistachio'),
    ('PEANUT', 'NUTS_SEEDS', 'MASS', 100, 0.60, ARRAY['PEANUT']::text[], 95, 1, 'kg', 'Cacahuate natural', 'Natural peanut'),
    ('PEANUT_BUTTER', 'NUTS_SEEDS', 'MASS', 98, 1.08, ARRAY['PEANUT']::text[], 110, 500, 'g', 'Crema de cacahuate', 'Peanut butter'),
    ('SESAME', 'NUTS_SEEDS', 'MASS', 100, 0.61, ARRAY['SESAME']::text[], 80, 500, 'g', 'Ajonjolí', 'Sesame seeds'),
    ('CHIA', 'NUTS_SEEDS', 'MASS', 100, 0.65, '{}'::text[], 90, 500, 'g', 'Chía', 'Chia seeds'),
    ('COCONUT_SHRED', 'NUTS_SEEDS', 'MASS', 100, 0.35, '{}'::text[], 120, 1, 'kg', 'Coco rallado', 'Shredded coconut'),
    ('SUNFLOWER_SEEDS', 'NUTS_SEEDS', 'MASS', 100, 0.55, '{}'::text[], 70, 500, 'g', 'Semilla de girasol', 'Sunflower seeds'),
    ('PUMPKIN_SEEDS', 'NUTS_SEEDS', 'MASS', 100, 0.55, '{}'::text[], 160, 1, 'kg', 'Pepita de calabaza', 'Pumpkin seeds'),
    ('STRAWBERRY', 'FRUITS', 'MASS', 90, NULL, '{}'::text[], 70, 1, 'kg', 'Fresa', 'Strawberry'),
    ('BANANA', 'FRUITS', 'MASS', 65, NULL, '{}'::text[], 28, 1, 'kg', 'Plátano', 'Banana'),
    ('APPLE', 'FRUITS', 'MASS', 80, NULL, '{}'::text[], 48, 1, 'kg', 'Manzana', 'Apple'),
    ('LEMON', 'FRUITS', 'COUNT', 100, NULL, '{}'::text[], 30, 12, 'pz', 'Limón (jugo y ralladura)', 'Lime (juice and zest)'),
    ('ORANGE', 'FRUITS', 'COUNT', 100, NULL, '{}'::text[], 30, 10, 'pz', 'Naranja', 'Orange'),
    ('BLUEBERRY', 'FRUITS', 'MASS', 98, NULL, '{}'::text[], 70, 170, 'g', 'Mora azul', 'Blueberry'),
    ('RASPBERRY', 'FRUITS', 'MASS', 95, NULL, '{}'::text[], 70, 170, 'g', 'Frambuesa', 'Raspberry'),
    ('MANGO', 'FRUITS', 'MASS', 65, NULL, '{}'::text[], 45, 1, 'kg', 'Mango', 'Mango'),
    ('PINEAPPLE', 'FRUITS', 'MASS', 55, NULL, '{}'::text[], 30, 1, 'kg', 'Piña', 'Pineapple'),
    ('PEACH_CANNED', 'FRUITS', 'MASS', 60, NULL, ARRAY['SULPHITES']::text[], 42, 820, 'g', 'Durazno en almíbar', 'Peaches in syrup'),
    ('RAISINS', 'FRUITS', 'MASS', 100, 0.65, ARRAY['SULPHITES']::text[], 110, 1, 'kg', 'Pasas', 'Raisins'),
    ('CARROT', 'FRUITS', 'MASS', 85, NULL, '{}'::text[], 22, 1, 'kg', 'Zanahoria', 'Carrot'),
    ('PUMPKIN_PUREE', 'FRUITS', 'MASS', 100, 1.00, '{}'::text[], 65, 425, 'g', 'Puré de calabaza', 'Pumpkin purée'),
    ('FRUIT_JAM', 'FRUITS', 'MASS', 98, 1.30, '{}'::text[], 55, 470, 'g', 'Mermelada de fresa', 'Strawberry jam'),
    ('VANILLA_EXTRACT', 'FLAVORINGS', 'VOLUME', 100, 0.88, '{}'::text[], 180, 250, 'ml', 'Extracto de vainilla', 'Vanilla extract'),
    ('VANILLA_BEAN', 'FLAVORINGS', 'COUNT', 100, NULL, '{}'::text[], 120, 2, 'pz', 'Vaina de vainilla', 'Vanilla bean'),
    ('ALMOND_EXTRACT', 'FLAVORINGS', 'VOLUME', 100, 0.88, ARRAY['TREE_NUTS']::text[], 140, 60, 'ml', 'Extracto de almendra', 'Almond extract'),
    ('INSTANT_COFFEE', 'FLAVORINGS', 'MASS', 100, 0.30, '{}'::text[], 150, 200, 'g', 'Café soluble', 'Instant coffee'),
    ('RUM', 'FLAVORINGS', 'VOLUME', 100, 0.95, '{}'::text[], 220, 750, 'ml', 'Ron', 'Rum'),
    ('SALT', 'SPICES', 'MASS', 100, 1.20, '{}'::text[], 15, 1, 'kg', 'Sal fina', 'Fine salt'),
    ('CINNAMON_GROUND', 'SPICES', 'MASS', 100, 0.50, '{}'::text[], 60, 100, 'g', 'Canela molida', 'Ground cinnamon'),
    ('NUTMEG', 'SPICES', 'MASS', 100, 0.50, '{}'::text[], 55, 50, 'g', 'Nuez moscada molida', 'Ground nutmeg'),
    ('GINGER_GROUND', 'SPICES', 'MASS', 100, 0.45, '{}'::text[], 45, 50, 'g', 'Jengibre molido', 'Ground ginger'),
    ('CLOVE_GROUND', 'SPICES', 'MASS', 100, 0.45, '{}'::text[], 50, 50, 'g', 'Clavo molido', 'Ground clove'),
    ('GELATIN_POWDER', 'THICKENERS', 'MASS', 100, 0.65, '{}'::text[], 120, 250, 'g', 'Grenetina en polvo', 'Unflavoured gelatin powder'),
    ('AGAR', 'THICKENERS', 'MASS', 100, 0.60, '{}'::text[], 160, 100, 'g', 'Agar agar', 'Agar agar'),
    ('PECTIN', 'THICKENERS', 'MASS', 100, 0.60, '{}'::text[], 140, 100, 'g', 'Pectina', 'Pectin'),
    ('XANTHAN', 'THICKENERS', 'MASS', 100, 0.60, '{}'::text[], 150, 100, 'g', 'Goma xantana', 'Xanthan gum'),
    ('GELATIN_STRAWBERRY', 'THICKENERS', 'MASS', 100, NULL, '{}'::text[], 14, 120, 'g', 'Gelatina sabor fresa (polvo)', 'Strawberry gelatin mix'),
    ('GELATIN_LIME', 'THICKENERS', 'MASS', 100, NULL, '{}'::text[], 14, 120, 'g', 'Gelatina sabor limón (polvo)', 'Lime gelatin mix'),
    ('FONDANT', 'DECORATION', 'MASS', 95, NULL, '{}'::text[], 150, 1, 'kg', 'Fondant blanco', 'White fondant'),
    ('SPRINKLES', 'DECORATION', 'MASS', 100, 0.80, '{}'::text[], 120, 500, 'g', 'Chochitos / sprinkles', 'Sprinkles'),
    ('MERINGUE_POWDER', 'DECORATION', 'MASS', 100, 0.50, ARRAY['EGG']::text[], 160, 250, 'g', 'Merengue en polvo', 'Meringue powder'),
    ('EDIBLE_GLITTER', 'DECORATION', 'MASS', 100, NULL, '{}'::text[], 95, 10, 'g', 'Brillo comestible', 'Edible glitter'),
    ('FOOD_COLOR_GEL', 'COLORANTS', 'MASS', 100, 1.20, '{}'::text[], 45, 20, 'g', 'Colorante en gel', 'Gel food colouring'),
    ('FOOD_COLOR_LIQUID', 'COLORANTS', 'VOLUME', 100, 1.00, '{}'::text[], 25, 30, 'ml', 'Colorante líquido', 'Liquid food colouring'),
    ('COOKIE_MARIA', 'OTHER', 'MASS', 100, NULL, ARRAY['GLUTEN', 'MILK', 'SOY']::text[], 22, 170, 'g', 'Galleta María', 'Maria cookies'),
    ('GRAHAM_CRACKER', 'OTHER', 'MASS', 100, NULL, ARRAY['GLUTEN', 'SOY']::text[], 48, 250, 'g', 'Galleta graham', 'Graham crackers'),
    ('LADYFINGERS', 'OTHER', 'COUNT', 100, NULL, ARRAY['GLUTEN', 'EGG']::text[], 75, 24, 'pz', 'Soletas', 'Ladyfingers'),
    ('CORNFLAKES', 'OTHER', 'MASS', 100, 0.12, '{}'::text[], 70, 500, 'g', 'Hojuelas de maíz', 'Cornflakes'),
    ('WATER', 'OTHER', 'VOLUME', 100, 1.00, '{}'::text[], 12, 1, 'L', 'Agua purificada', 'Purified water'),
    ('CAKE_BOARD_25', 'PACKAGING', 'COUNT', 100, NULL, '{}'::text[], 12, 1, 'pz', 'Base de cartón 25 cm', 'Cake board 25 cm'),
    ('CAKE_BOX_25', 'PACKAGING', 'COUNT', 100, NULL, '{}'::text[], 22, 1, 'pz', 'Caja para pastel 25 cm', 'Cake box 25 cm'),
    ('CUPCAKE_LINER', 'PACKAGING', 'COUNT', 100, NULL, '{}'::text[], 35, 100, 'pz', 'Capacillo', 'Cupcake liner'),
    ('CLEAR_DOME_IND', 'PACKAGING', 'COUNT', 100, NULL, '{}'::text[], 4, 1, 'pz', 'Domo individual', 'Individual clear dome');

INSERT INTO ingredients (id, code, owner_id, category_id, base_dimension, yield_percent, density_g_per_ml, status)
SELECT gen_random_uuid(), s.code, NULL, c.id, s.dim, s.yield_percent, s.density, 'ACTIVE'
FROM seed_ingredients s
JOIN categories c ON c.scope = 'SYSTEM' AND c.type = 'INGREDIENT' AND c.code = s.category
ON CONFLICT (owner_key, code) DO NOTHING;

INSERT INTO ingredient_i18n (ingredient_id, locale, name)
SELECT i.id, l.locale, CASE l.locale WHEN 'es' THEN s.name_es ELSE s.name_en END
FROM seed_ingredients s JOIN ingredients i ON i.code = s.code AND i.owner_id IS NULL
CROSS JOIN (VALUES ('es'), ('en')) l(locale)
ON CONFLICT (ingredient_id, locale) DO NOTHING;

INSERT INTO ingredient_allergens (ingredient_id, allergen_id)
SELECT i.id, a.id
FROM seed_ingredients s
JOIN ingredients i ON i.code = s.code AND i.owner_id IS NULL
CROSS JOIN unnest(s.allergen_codes) ac
JOIN allergens a ON a.code = ac AND a.owner_id IS NULL
ON CONFLICT DO NOTHING;

-- Reference price only where none exists yet (history is append-only).
INSERT INTO ingredient_prices (id, ingredient_id, owner_id, purchase_quantity, purchase_unit_id, price, currency, supplier,
                               priced_at, cost_per_base_unit, recorded_at)
SELECT gen_random_uuid(), i.id, NULL, s.purchase_quantity, mu.id, s.price, 'MXN', 'Referencia Cronos', DATE '2026-10-01',
       round(s.price / (s.purchase_quantity * mu.multiplier_to_base
                        * CASE WHEN ut.dimension = i.base_dimension THEN 1
                               WHEN ut.dimension = 'VOLUME' AND i.base_dimension = 'MASS' THEN i.density_g_per_ml
                               ELSE 1 / i.density_g_per_ml END
                        * i.yield_percent / 100), 8),
       now()
FROM seed_ingredients s
JOIN ingredients i ON i.code = s.code AND i.owner_id IS NULL
JOIN measurement_units mu ON mu.code_identity = s.unit_code AND mu.deleted_at IS NULL
JOIN unit_types ut ON ut.id = mu.unit_type_id
WHERE NOT EXISTS (SELECT 1 FROM ingredient_prices p WHERE p.ingredient_id = i.id AND p.owner_id IS NULL);

-- ─── substitutes (§10.4) ────────────────────────────────────────────────────────────────
INSERT INTO ingredient_substitutes (ingredient_id, substitute_id, owner_id, ratio, notes)
SELECT o.id, s.id, NULL, v.ratio, v.notes
FROM (VALUES
    ('BUTTER_UNSALTED', 'VEGAN_BUTTER', 1, 'Sin lácteos'),
    ('BUTTER_UNSALTED', 'MARGARINE', 1, 'Sin lácteos; contiene soya'),
    ('BUTTER_UNSALTED', 'OIL_COCONUT', 0.8, 'Para masas batidas'),
    ('MILK_WHOLE', 'ALMOND_MILK', 1, 'Sin lácteos; contiene frutos de cáscara'),
    ('MILK_WHOLE', 'OAT_MILK', 1, 'Sin lácteos; contiene gluten'),
    ('MILK_WHOLE', 'COCONUT_MILK', 1, 'Sin lácteos'),
    ('HEAVY_CREAM', 'COCONUT_MILK', 1, 'Usar la parte sólida refrigerada'),
    ('EGG_WHITE', 'AQUAFABA', 1, 'Para merengues'),
    ('WHEAT_FLOUR_AP', 'GF_FLOUR_BLEND', 1, 'Sin gluten; añadir goma xantana si la mezcla no la trae'),
    ('CAKE_FLOUR', 'GF_FLOUR_BLEND', 1, 'Sin gluten'),
    ('CAKE_FLOUR', 'ALMOND_FLOUR', 1, 'Sin gluten; contiene frutos de cáscara; reduce estructura'),
    ('CHOC_SEMISWEET', 'CHOC_MEXICAN', 1, 'Sin lácteos ni soya; textura más rústica'),
    ('WALNUT', 'SUNFLOWER_SEEDS', 1, 'Sin frutos de cáscara'),
    ('PECAN', 'PUMPKIN_SEEDS', 1, 'Sin frutos de cáscara'),
    ('PEANUT_BUTTER', 'SUNFLOWER_SEEDS', 1, 'Moler para pasta; sin cacahuate'),
    ('CONDENSED_MILK', 'COCONUT_MILK', 1.2, 'Reducir con azúcar; sin lácteos'),
    ('GELATIN_POWDER', 'AGAR', 0.33, 'Vegetal; hervir 2 min'),
    ('SUGAR_WHITE', 'ERYTHRITOL', 1, 'Sin azúcar'),
    ('COOKIE_MARIA', 'GRAHAM_CRACKER', 1, 'Base de pay')
) v(original, substitute, ratio, notes)
JOIN ingredients o ON o.code = v.original AND o.owner_id IS NULL
JOIN ingredients s ON s.code = v.substitute AND s.owner_id IS NULL
ON CONFLICT DO NOTHING;
