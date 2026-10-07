-- Moves pre-kitchen data into the kitchen model (kitchen-catalog-and-recipes.md §9, §13).
-- Legacy tables stay (read-only) for one release. Ids are preserved wherever the target keeps UUIDs, so
-- recipe shares, quotes and the deprecated endpoints keep resolving. Costs are recomputed by the app:
-- every migrated recipe is marked STALE and queued for recalculation.

-- ─── allergens: legacy rows adopt a SYSTEM allergen by name, the rest become SYSTEM rows ──
CREATE TEMP TABLE legacy_allergen_map (legacy_id UUID PRIMARY KEY, allergen_id BIGINT NOT NULL) ON COMMIT DROP;

INSERT INTO legacy_allergen_map (legacy_id, allergen_id)
SELECT DISTINCT ON (l.id) l.id, a.id
FROM legacy_allergens l
JOIN allergens a ON a.owner_id IS NULL
WHERE a.code IN (kitchen_code(l.name, 'ALG', 50), kitchen_code(coalesce(l.alternative_name, l.name), 'ALG', 50))
   OR EXISTS (SELECT 1 FROM allergen_i18n n WHERE n.allergen_id = a.id
              AND kitchen_fold(n.name) IN (kitchen_fold(l.name), kitchen_fold(coalesce(l.alternative_name, l.name))))
-- an exact name beats a code match
ORDER BY l.id, NOT EXISTS (SELECT 1 FROM allergen_i18n n WHERE n.allergen_id = a.id AND kitchen_fold(n.name) = kitchen_fold(l.name)), a.id;

DO $$
DECLARE
    l RECORD;
    base TEXT;
    new_code TEXT;
    new_id BIGINT;
BEGIN
    FOR l IN SELECT * FROM legacy_allergens WHERE id NOT IN (SELECT legacy_id FROM legacy_allergen_map) ORDER BY created_at, id LOOP
        base := kitchen_code(coalesce(nullif(btrim(l.alternative_name), ''), l.name), 'ALG', 44);
        new_code := base;
        IF EXISTS (SELECT 1 FROM allergens WHERE owner_id IS NULL AND code = new_code) THEN
            new_code := left(base, 40) || '_' || upper(substr(md5(l.id::text), 1, 6));
        END IF;
        INSERT INTO allergens (code, owner_id, icon, regulations, status, created_at)
        VALUES (new_code, NULL, 'pi pi-exclamation-triangle', '{}',
                CASE WHEN l.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END, coalesce(l.created_at, now()))
        RETURNING id INTO new_id;
        INSERT INTO allergen_i18n (allergen_id, locale, name, description)
        VALUES (new_id, 'es', left(l.name, 80), left(l.description, 500)),
               (new_id, 'en', left(coalesce(nullif(btrim(l.alternative_name), ''), l.name), 80), left(l.description, 500));
        INSERT INTO legacy_allergen_map VALUES (l.id, new_id);
    END LOOP;
END $$;

-- The deprecated /allergen API keeps answering with the old UUIDs (first legacy row wins).
UPDATE allergens a SET legacy_id = m.legacy_id
FROM (SELECT DISTINCT ON (allergen_id) allergen_id, legacy_id FROM legacy_allergen_map ORDER BY allergen_id, legacy_id) m
WHERE a.id = m.allergen_id;

-- ─── ingredients from raw_materials ─────────────────────────────────────────────────────
-- Platform raw materials that already exist in the V18 catalog map onto it; everything else keeps its id.
CREATE TEMP TABLE legacy_ingredient_map (raw_id UUID PRIMARY KEY, ingredient_id UUID NOT NULL) ON COMMIT DROP;

INSERT INTO legacy_ingredient_map (raw_id, ingredient_id)
SELECT DISTINCT ON (r.id) r.id, i.id
FROM raw_materials r
JOIN ingredient_i18n n ON kitchen_fold(n.name) = kitchen_fold(r.name)
JOIN ingredients i ON i.id = n.ingredient_id AND i.owner_id IS NULL
WHERE r.user_id IS NULL
ORDER BY r.id, i.id;

CREATE TEMP TABLE legacy_raw ON COMMIT DROP AS
SELECT r.*,
       CASE ut.dimension WHEN 'MASS' THEN 'MASS' WHEN 'VOLUME' THEN 'VOLUME' ELSE 'COUNT' END AS dimension,
       ut.dimension AS unit_dimension,
       mu.multiplier_to_base,
       coalesce((SELECT c.id FROM categories c WHERE c.id = r.category_id),
                (SELECT c.id FROM categories c WHERE c.scope = 'SYSTEM' AND c.type = 'INGREDIENT' AND c.code = 'OTHER')) AS target_category,
       row_number() OVER (PARTITION BY r.user_id, kitchen_code(r.name, 'ING', 44) ORDER BY r.created_at, r.id) AS code_rank
FROM raw_materials r
JOIN measurement_units mu ON mu.id = r.purchase_unit_id
JOIN unit_types ut ON ut.id = mu.unit_type_id
WHERE r.id NOT IN (SELECT raw_id FROM legacy_ingredient_map);

INSERT INTO ingredients (id, code, owner_id, category_id, base_dimension, yield_percent, density_g_per_ml, brand, status, version,
                         created_at, updated_at)
SELECT r.id,
       CASE WHEN r.code_rank = 1 AND NOT EXISTS (SELECT 1 FROM ingredients o WHERE o.owner_key = coalesce(r.user_id, '00000000-0000-0000-0000-000000000000'::uuid)
                                                   AND o.code = kitchen_code(r.name, 'ING', 44))
            THEN kitchen_code(r.name, 'ING', 44)
            ELSE left(kitchen_code(r.name, 'ING', 44), 40) || '_' || upper(substr(md5(r.id::text), 1, 6)) END,
       r.user_id, r.target_category, r.dimension,
       greatest(1, least(100, round(r.yield_percentage, 1))),
       CASE WHEN r.dimension <> 'COUNT' AND r.density BETWEEN 0.1 AND 3 THEN round(r.density, 3) END,
       left(nullif(btrim(r.brand), ''), 80),
       CASE WHEN r.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
       0,
       r.created_at AT TIME ZONE 'America/Mexico_City',
       r.updated_at AT TIME ZONE 'America/Mexico_City'
FROM legacy_raw r
WHERE r.target_category IS NOT NULL;

INSERT INTO legacy_ingredient_map (raw_id, ingredient_id)
SELECT r.id, r.id FROM legacy_raw r JOIN ingredients i ON i.id = r.id;

INSERT INTO ingredient_i18n (ingredient_id, locale, name, description)
SELECT r.id, l.locale, left(r.name, 120), left(nullif(btrim(r.description), ''), 500)
FROM legacy_raw r
JOIN ingredients i ON i.id = r.id
CROSS JOIN (VALUES ('es'), ('en')) l(locale);

-- One price per migrated raw material, priced on its last price update (same formula as PurchaseCost).
INSERT INTO ingredient_prices (id, ingredient_id, owner_id, purchase_quantity, purchase_unit_id, price, currency, supplier,
                               priced_at, cost_per_base_unit, recorded_at)
SELECT gen_random_uuid(), i.id, r.user_id, r.purchase_quantity, r.purchase_unit_id, r.unit_cost, upper(r.currency),
       left(nullif(btrim(r.supplier), ''), 120),
       coalesce(r.last_price_update, r.updated_at, r.created_at)::date,
       round(r.unit_cost / (r.purchase_quantity * r.multiplier_to_base * i.yield_percent / 100), 8),
       now()
FROM legacy_raw r
JOIN ingredients i ON i.id = r.id
WHERE r.unit_cost > 0 AND r.purchase_quantity > 0 AND r.multiplier_to_base > 0 AND r.unit_dimension = i.base_dimension;

INSERT INTO ingredient_allergens (ingredient_id, allergen_id)
SELECT DISTINCT m.ingredient_id, a.allergen_id
FROM raw_material_allergens ra
JOIN legacy_ingredient_map m ON m.raw_id = ra.raw_material_id
JOIN legacy_allergen_map a ON a.legacy_id = ra.allergen_id
WHERE m.raw_id = m.ingredient_id -- never edits V18 platform rows
ON CONFLICT DO NOTHING;

-- ─── substitutes (tenant-declared) ──────────────────────────────────────────────────────
-- original_ingredient_id was a raw material or, in some flows, a recipe line: both resolve here.
INSERT INTO ingredient_substitutes (ingredient_id, substitute_id, owner_id, ratio, notes)
SELECT DISTINCT ON (o.ingredient_id, s.ingredient_id, l.user_id)
       o.ingredient_id, s.ingredient_id, l.user_id, greatest(0.01, least(10, round(l.conversion_ratio, 3))), left(l.notes, 200)
FROM legacy_ingredient_substitutes l
JOIN legacy_ingredient_map o ON o.raw_id = coalesce((SELECT ri.raw_material_id FROM recipe_ingredients ri WHERE ri.id = l.original_ingredient_id),
                                                    l.original_ingredient_id)
JOIN legacy_ingredient_map s ON s.raw_id = l.substitute_material_id
WHERE o.ingredient_id <> s.ingredient_id
ORDER BY o.ingredient_id, s.ingredient_id, l.user_id, l.created_at DESC
ON CONFLICT DO NOTHING;

-- ─── recipe lines, fixed costs, files ───────────────────────────────────────────────────
INSERT INTO recipe_lines (id, recipe_id, ingredient_id, quantity, unit_id, optional, quote_selectable, notes, display_order)
SELECT ri.id, ri.recipe_id, m.ingredient_id, ri.quantity, ri.unit_id, coalesce(ri.is_optional, FALSE), coalesce(ri.is_optional, FALSE),
       left(nullif(btrim(ri.notes), ''), 200),
       coalesce(ri.display_order, row_number() OVER (PARTITION BY ri.recipe_id ORDER BY ri.created_at, ri.id)::int)
FROM recipe_ingredients ri
JOIN legacy_ingredient_map m ON m.raw_id = ri.raw_material_id
JOIN recipes r ON r.id = ri.recipe_id
JOIN measurement_units mu ON mu.id = ri.unit_id
WHERE ri.quantity > 0
ON CONFLICT (id) DO NOTHING;

INSERT INTO recipe_fixed_costs (id, recipe_id, user_fixed_cost_id, minutes, percentage)
SELECT DISTINCT ON (f.recipe_id, f.master_fixed_cost_id) f.id, f.recipe_id, f.master_fixed_cost_id, f.time_in_minutes, f.percentage
FROM legacy_recipe_fixed_costs f
JOIN recipes r ON r.id = f.recipe_id
JOIN user_fixed_costs u ON u.id = f.master_fixed_cost_id
WHERE coalesce(f.is_active, TRUE)
ORDER BY f.recipe_id, f.master_fixed_cost_id, f.created_at DESC
ON CONFLICT DO NOTHING;

INSERT INTO recipe_files (id, recipe_id, storage_key, file_name, kind, mime_type, size_bytes, description, is_cover, thumbnail_key, uploaded_at)
SELECT f.id, f.recipe_id, left(f.file_path, 300), left(coalesce(nullif(f.original_file_name, ''), f.file_name), 150),
       CASE WHEN f.mime_type LIKE 'image/%' THEN 'IMAGE'
            WHEN f.mime_type = 'application/pdf' THEN 'PDF'
            WHEN f.mime_type LIKE 'video/%' THEN 'VIDEO'
            WHEN f.mime_type ~* '(spreadsheet|excel|csv)' THEN 'SPREADSHEET'
            ELSE 'DOCUMENT' END,
       coalesce(f.mime_type, 'application/octet-stream'), coalesce(f.file_size, 0), left(f.description, 200),
       coalesce(f.is_primary, FALSE) AND row_number() OVER (PARTITION BY f.recipe_id, coalesce(f.is_primary, FALSE) ORDER BY f.created_at, f.id) = 1,
       left(f.thumbnail_path, 300),
       f.created_at AT TIME ZONE 'America/Mexico_City'
FROM legacy_recipe_files f
JOIN recipes r ON r.id = f.recipe_id
ON CONFLICT (id) DO NOTHING;

-- ─── recalculation ──────────────────────────────────────────────────────────────────────
UPDATE recipes SET cost_status = 'STALE' WHERE deleted_at IS NULL AND cost_calculated_at IS NULL;

INSERT INTO kitchen_jobs (kind, payload)
SELECT 'RECALCULATE_RECIPES',
       jsonb_build_object('ingredientId', NULL::uuid, 'recipeIds', jsonb_agg(id), 'reasonKey', 'kitchen.revision.migrated',
                          'reasonArgs', '[]'::jsonb, 'actor', NULL::uuid)
FROM (SELECT id, (row_number() OVER (ORDER BY id) - 1) / 200 AS batch FROM recipes WHERE deleted_at IS NULL AND cost_calculated_at IS NULL) b
GROUP BY batch;
