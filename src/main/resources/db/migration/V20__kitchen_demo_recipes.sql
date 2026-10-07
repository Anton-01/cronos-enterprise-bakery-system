-- SYSTEM recipe library (kitchen-catalog-and-recipes.md §10.5): 10 read-only templates tenants duplicate.
-- Ids derive from the code so re-runs and environments agree. Costs come from the app: the recipes are
-- queued for recalculation, which writes revision v1 ("Receta creada desde la biblioteca Cronos").

CREATE TEMP TABLE seed_recipes (
    code TEXT, name TEXT, category TEXT, difficulty TEXT, yield_quantity NUMERIC, yield_unit TEXT,
    prep INT, bake INT, cool INT, oven INT, shelf_life INT, margin NUMERIC, refrigerated BOOLEAN, description TEXT, steps TEXT[]
) ON COMMIT DROP;

INSERT INTO seed_recipes VALUES
    ('CHOC_CHIP_COOKIES', 'Galletas con chispas de chocolate', 'COOKIES', 'EASY', 24, 'piezas', 20, 12, 15, 180, 7, 60, FALSE,
     'Galletas clásicas de mantequilla con chispas de chocolate y nuez opcional.',
     ARRAY['Acremar mantequilla con azúcares 3 min.', 'Incorporar huevos y vainilla.', 'Añadir secos tamizados.',
           'Agregar chispas (y nuez).', 'Porcionar 40 g, refrigerar 30 min.', 'Hornear 11–12 min a 180 °C.']),
    ('BROWNIES', 'Brownies', 'INDIVIDUAL_DESSERTS', 'EASY', 16, 'piezas', 20, 28, 30, 175, 5, 60, FALSE,
     'Brownies húmedos de chocolate semiamargo con nuez pecana opcional.',
     ARRAY['Fundir chocolate con mantequilla a baño maría.', 'Batir huevos con azúcar y vainilla hasta espumar.',
           'Integrar el chocolate tibio.', 'Añadir harina, cocoa y sal tamizadas.', 'Verter en molde engrasado y cubrir con nuez.',
           'Hornear 28 min a 175 °C y enfriar antes de cortar.']),
    ('BANANA_BREAD', 'Panqué de plátano', 'BREADS', 'EASY', 10, 'rebanadas', 15, 55, 30, 175, 5, 60, FALSE,
     'Panqué húmedo de plátano maduro con canela.',
     ARRAY['Machacar los plátanos.', 'Mezclar con azúcar, aceite, huevos y vainilla.', 'Añadir harina, bicarbonato, canela y sal.',
           'Incorporar la nuez.', 'Hornear 55 min a 175 °C en molde de panqué.']),
    ('MOSAIC_GELATIN', 'Gelatina de mosaico', 'GELATINS', 'EASY', 12, 'porciones', 30, 0, 240, NULL, 3, 60, TRUE,
     'Cubos de gelatina de sabores en base de leche.',
     ARRAY['Preparar las gelatinas de sabor con el agua y cuajar.', 'Cortar en cubos.', 'Hidratar la grenetina y disolverla tibia.',
           'Mezclar leches, vainilla y grenetina.', 'Verter sobre los cubos y refrigerar 4 h.']),
    ('KEY_LIME_PIE', 'Pay de limón', 'PIES_TARTS', 'EASY', 10, 'porciones', 25, 0, 180, NULL, 4, 60, TRUE,
     'Pay frío de limón con base de galleta María.',
     ARRAY['Moler las galletas y mezclar con mantequilla fundida.', 'Forrar el molde y refrigerar.',
           'Licuar leches, queso crema y jugo de limón.', 'Verter sobre la base.', 'Refrigerar 3 h y decorar con limón.']),
    ('TRES_LECHES_CAKE', 'Pastel de tres leches', 'CAKES', 'MEDIUM', 20, 'porciones', 40, 40, 240, 175, 4, 65, TRUE,
     'Bizcocho esponjoso bañado en tres leches con crema batida y fresas.',
     ARRAY['Batir claras a punto de nieve y añadir yemas y azúcar.', 'Incorporar harina y polvo para hornear alternando con leche.',
           'Hornear 40 min a 175 °C.', 'Mezclar las tres leches (y ron) y bañar el bizcocho tibio.', 'Refrigerar 4 h.',
           'Cubrir con crema batida con azúcar glass y decorar con fresas.']),
    ('NY_CHEESECAKE', 'Cheesecake estilo New York', 'CHEESECAKES', 'ADVANCED', 16, 'porciones', 30, 75, 600, 160, 5, 60, TRUE,
     'Cheesecake denso horneado a baño maría con cubierta de mora azul.',
     ARRAY['Mezclar galleta molida, mantequilla y azúcar; hornear la base 10 min.', 'Batir queso crema con azúcar sin airear.',
           'Añadir huevos uno a uno, crema ácida, harina, vainilla y ralladura.', 'Hornear 75 min a 160 °C a baño maría.',
           'Enfriar en el horno apagado y refrigerar toda la noche.', 'Cocer moras con azúcar y fécula para la cubierta.']),
    ('CHOCOLATE_GANACHE_CAKE', 'Pastel de chocolate con ganache', 'CAKES', 'ADVANCED', 24, 'porciones', 45, 45, 120, 175, 5, 60, TRUE,
     'Bizcocho de chocolate con café cubierto de ganache semiamargo.',
     ARRAY['Mezclar secos.', 'Añadir huevos, buttermilk, aceite y vainilla.', 'Incorporar café disuelto en agua caliente.',
           'Hornear 45 min a 175 °C en dos moldes.', 'Calentar crema y verter sobre el chocolate con mantequilla.',
           'Rellenar, cubrir con ganache y decorar.']),
    ('CARROT_CAKE', 'Pastel de zanahoria con betún de queso crema', 'CAKES', 'MEDIUM', 20, 'porciones', 50, 50, 120, 175, 5, 60, TRUE,
     'Pastel especiado de zanahoria y piña con betún de queso crema.',
     ARRAY['Batir huevos con azúcar y aceite.', 'Añadir zanahoria rallada y piña escurrida.', 'Integrar secos y especias.',
           'Agregar nuez y pasas.', 'Hornear 50 min a 175 °C.', 'Batir queso crema, mantequilla, azúcar glass y vainilla para el betún.']),
    ('BERRY_TART', 'Tarta de frutos rojos con crema pastelera', 'PASTRIES', 'ADVANCED', 12, 'porciones', 60, 35, 120, 180, 2, 60, TRUE,
     'Tarta sablée con crema pastelera de vainilla y frutos rojos frescos.',
     ARRAY['Arenar harina, mantequilla, azúcar glass, almendra y sal; ligar con huevo.', 'Refrigerar, estirar y hornear en blanco 35 min a 180 °C.',
           'Cocer leche con vainilla; temperar yemas con azúcar y fécula.', 'Espesar la crema e incorporar la mantequilla.',
           'Rellenar la tarta fría y acomodar la fruta.', 'Abrillantar con mermelada caliente.']);

-- flag: '' plain, '*' optional & quote-selectable, '+' quote-selectable only.
CREATE TEMP TABLE seed_recipe_lines (recipe TEXT, position INT, section TEXT, ingredient TEXT, quantity NUMERIC, unit TEXT, flag TEXT) ON COMMIT DROP;
INSERT INTO seed_recipe_lines VALUES
    ('CHOC_CHIP_COOKIES', 1, 'Masa', 'WHEAT_FLOUR_AP', 280, 'g', ''), ('CHOC_CHIP_COOKIES', 2, 'Masa', 'BUTTER_UNSALTED', 170, 'g', ''),
    ('CHOC_CHIP_COOKIES', 3, 'Masa', 'SUGAR_BROWN', 150, 'g', ''), ('CHOC_CHIP_COOKIES', 4, 'Masa', 'SUGAR_WHITE', 100, 'g', ''),
    ('CHOC_CHIP_COOKIES', 5, 'Masa', 'EGG_WHOLE', 2, 'pz', ''), ('CHOC_CHIP_COOKIES', 6, 'Masa', 'VANILLA_EXTRACT', 10, 'ml', ''),
    ('CHOC_CHIP_COOKIES', 7, 'Masa', 'BAKING_SODA', 5, 'g', ''), ('CHOC_CHIP_COOKIES', 8, 'Masa', 'SALT', 3, 'g', ''),
    ('CHOC_CHIP_COOKIES', 9, 'Masa', 'CHOC_CHIPS', 200, 'g', ''), ('CHOC_CHIP_COOKIES', 10, 'Extras', 'WALNUT', 80, 'g', '*'),

    ('BROWNIES', 1, 'Masa', 'CHOC_SEMISWEET', 200, 'g', ''), ('BROWNIES', 2, 'Masa', 'BUTTER_UNSALTED', 150, 'g', ''),
    ('BROWNIES', 3, 'Masa', 'SUGAR_WHITE', 220, 'g', ''), ('BROWNIES', 4, 'Masa', 'EGG_WHOLE', 4, 'pz', ''),
    ('BROWNIES', 5, 'Masa', 'WHEAT_FLOUR_AP', 110, 'g', ''), ('BROWNIES', 6, 'Masa', 'COCOA_POWDER', 30, 'g', ''),
    ('BROWNIES', 7, 'Masa', 'SALT', 2, 'g', ''), ('BROWNIES', 8, 'Masa', 'VANILLA_EXTRACT', 5, 'ml', ''),
    ('BROWNIES', 9, 'Cobertura', 'PECAN', 100, 'g', '*'),

    ('BANANA_BREAD', 1, 'Masa', 'BANANA', 450, 'g', ''), ('BANANA_BREAD', 2, 'Masa', 'WHEAT_FLOUR_AP', 250, 'g', ''),
    ('BANANA_BREAD', 3, 'Masa', 'SUGAR_BROWN', 150, 'g', ''), ('BANANA_BREAD', 4, 'Masa', 'OIL_CANOLA', 100, 'ml', ''),
    ('BANANA_BREAD', 5, 'Masa', 'EGG_WHOLE', 2, 'pz', ''), ('BANANA_BREAD', 6, 'Masa', 'BAKING_SODA', 6, 'g', ''),
    ('BANANA_BREAD', 7, 'Masa', 'CINNAMON_GROUND', 3, 'g', ''), ('BANANA_BREAD', 8, 'Masa', 'SALT', 2, 'g', ''),
    ('BANANA_BREAD', 9, 'Masa', 'VANILLA_EXTRACT', 5, 'ml', ''), ('BANANA_BREAD', 10, 'Extras', 'WALNUT', 80, 'g', '*'),

    ('MOSAIC_GELATIN', 1, 'Cubos', 'GELATIN_STRAWBERRY', 120, 'g', ''), ('MOSAIC_GELATIN', 2, 'Cubos', 'GELATIN_LIME', 120, 'g', ''),
    ('MOSAIC_GELATIN', 3, 'Cubos', 'WATER', 1000, 'ml', ''), ('MOSAIC_GELATIN', 4, 'Base', 'MILK_WHOLE', 500, 'ml', ''),
    ('MOSAIC_GELATIN', 5, 'Base', 'CONDENSED_MILK', 300, 'ml', ''), ('MOSAIC_GELATIN', 6, 'Base', 'MILK_EVAPORATED', 360, 'ml', ''),
    ('MOSAIC_GELATIN', 7, 'Base', 'GELATIN_POWDER', 28, 'g', ''), ('MOSAIC_GELATIN', 8, 'Base', 'VANILLA_EXTRACT', 5, 'ml', ''),

    ('KEY_LIME_PIE', 1, 'Base', 'COOKIE_MARIA', 340, 'g', ''), ('KEY_LIME_PIE', 2, 'Base', 'BUTTER_UNSALTED', 100, 'g', ''),
    ('KEY_LIME_PIE', 3, 'Relleno', 'CONDENSED_MILK', 300, 'ml', ''), ('KEY_LIME_PIE', 4, 'Relleno', 'MILK_EVAPORATED', 360, 'ml', ''),
    ('KEY_LIME_PIE', 5, 'Relleno', 'LEMON', 6, 'pz', ''), ('KEY_LIME_PIE', 6, 'Relleno', 'CREAM_CHEESE', 190, 'g', ''),
    ('KEY_LIME_PIE', 7, 'Decoración', 'LEMON', 1, 'pz', '+'),

    ('TRES_LECHES_CAKE', 1, 'Bizcocho', 'CAKE_FLOUR', 250, 'g', ''), ('TRES_LECHES_CAKE', 2, 'Bizcocho', 'SUGAR_WHITE', 200, 'g', ''),
    ('TRES_LECHES_CAKE', 3, 'Bizcocho', 'EGG_WHOLE', 6, 'pz', ''), ('TRES_LECHES_CAKE', 4, 'Bizcocho', 'BAKING_POWDER', 10, 'g', ''),
    ('TRES_LECHES_CAKE', 5, 'Bizcocho', 'MILK_WHOLE', 120, 'ml', ''), ('TRES_LECHES_CAKE', 6, 'Bizcocho', 'VANILLA_EXTRACT', 10, 'ml', ''),
    ('TRES_LECHES_CAKE', 7, 'Baño', 'MILK_EVAPORATED', 360, 'ml', ''), ('TRES_LECHES_CAKE', 8, 'Baño', 'CONDENSED_MILK', 300, 'ml', ''),
    ('TRES_LECHES_CAKE', 9, 'Baño', 'HEAVY_CREAM', 250, 'ml', ''), ('TRES_LECHES_CAKE', 10, 'Baño', 'RUM', 30, 'ml', '*'),
    ('TRES_LECHES_CAKE', 11, 'Cubierta', 'HEAVY_CREAM', 500, 'ml', ''), ('TRES_LECHES_CAKE', 12, 'Cubierta', 'SUGAR_POWDERED', 60, 'g', ''),
    ('TRES_LECHES_CAKE', 13, 'Decoración', 'STRAWBERRY', 300, 'g', '+'), ('TRES_LECHES_CAKE', 14, 'Decoración', 'CAKE_BOARD_25', 1, 'pz', ''),
    ('TRES_LECHES_CAKE', 15, 'Decoración', 'CAKE_BOX_25', 1, 'pz', ''),

    ('NY_CHEESECAKE', 1, 'Base', 'GRAHAM_CRACKER', 250, 'g', ''), ('NY_CHEESECAKE', 2, 'Base', 'BUTTER_UNSALTED', 100, 'g', ''),
    ('NY_CHEESECAKE', 3, 'Base', 'SUGAR_WHITE', 30, 'g', ''), ('NY_CHEESECAKE', 4, 'Relleno', 'CREAM_CHEESE', 900, 'g', ''),
    ('NY_CHEESECAKE', 5, 'Relleno', 'SUGAR_WHITE', 250, 'g', ''), ('NY_CHEESECAKE', 6, 'Relleno', 'EGG_WHOLE', 5, 'pz', ''),
    ('NY_CHEESECAKE', 7, 'Relleno', 'SOUR_CREAM', 200, 'g', ''), ('NY_CHEESECAKE', 8, 'Relleno', 'WHEAT_FLOUR_AP', 30, 'g', ''),
    ('NY_CHEESECAKE', 9, 'Relleno', 'VANILLA_EXTRACT', 10, 'ml', ''), ('NY_CHEESECAKE', 10, 'Relleno', 'LEMON', 1, 'pz', ''),
    ('NY_CHEESECAKE', 11, 'Cubierta', 'BLUEBERRY', 340, 'g', '+'), ('NY_CHEESECAKE', 12, 'Cubierta', 'SUGAR_WHITE', 80, 'g', '+'),
    ('NY_CHEESECAKE', 13, 'Cubierta', 'CORNSTARCH', 10, 'g', '+'),

    ('CHOCOLATE_GANACHE_CAKE', 1, 'Bizcocho', 'WHEAT_FLOUR_AP', 350, 'g', ''), ('CHOCOLATE_GANACHE_CAKE', 2, 'Bizcocho', 'SUGAR_WHITE', 400, 'g', ''),
    ('CHOCOLATE_GANACHE_CAKE', 3, 'Bizcocho', 'COCOA_POWDER', 90, 'g', ''), ('CHOCOLATE_GANACHE_CAKE', 4, 'Bizcocho', 'BAKING_SODA', 9, 'g', ''),
    ('CHOCOLATE_GANACHE_CAKE', 5, 'Bizcocho', 'BAKING_POWDER', 5, 'g', ''), ('CHOCOLATE_GANACHE_CAKE', 6, 'Bizcocho', 'SALT', 4, 'g', ''),
    ('CHOCOLATE_GANACHE_CAKE', 7, 'Bizcocho', 'EGG_WHOLE', 3, 'pz', ''), ('CHOCOLATE_GANACHE_CAKE', 8, 'Bizcocho', 'BUTTERMILK', 300, 'ml', ''),
    ('CHOCOLATE_GANACHE_CAKE', 9, 'Bizcocho', 'OIL_CANOLA', 150, 'ml', ''), ('CHOCOLATE_GANACHE_CAKE', 10, 'Bizcocho', 'INSTANT_COFFEE', 6, 'g', ''),
    ('CHOCOLATE_GANACHE_CAKE', 11, 'Bizcocho', 'WATER', 250, 'ml', ''), ('CHOCOLATE_GANACHE_CAKE', 12, 'Bizcocho', 'VANILLA_EXTRACT', 10, 'ml', ''),
    ('CHOCOLATE_GANACHE_CAKE', 13, 'Ganache', 'CHOC_SEMISWEET', 400, 'g', ''), ('CHOCOLATE_GANACHE_CAKE', 14, 'Ganache', 'HEAVY_CREAM', 400, 'ml', ''),
    ('CHOCOLATE_GANACHE_CAKE', 15, 'Ganache', 'BUTTER_UNSALTED', 40, 'g', ''), ('CHOCOLATE_GANACHE_CAKE', 16, 'Decoración', 'HAZELNUT', 100, 'g', '*'),
    ('CHOCOLATE_GANACHE_CAKE', 17, 'Decoración', 'CHOC_WHITE', 100, 'g', '*'), ('CHOCOLATE_GANACHE_CAKE', 18, 'Decoración', 'CAKE_BOARD_25', 1, 'pz', ''),
    ('CHOCOLATE_GANACHE_CAKE', 19, 'Decoración', 'CAKE_BOX_25', 1, 'pz', ''),

    ('CARROT_CAKE', 1, 'Bizcocho', 'WHEAT_FLOUR_AP', 300, 'g', ''), ('CARROT_CAKE', 2, 'Bizcocho', 'SUGAR_BROWN', 300, 'g', ''),
    ('CARROT_CAKE', 3, 'Bizcocho', 'OIL_CANOLA', 250, 'ml', ''), ('CARROT_CAKE', 4, 'Bizcocho', 'EGG_WHOLE', 4, 'pz', ''),
    ('CARROT_CAKE', 5, 'Bizcocho', 'CARROT', 350, 'g', ''), ('CARROT_CAKE', 6, 'Bizcocho', 'PINEAPPLE', 200, 'g', ''),
    ('CARROT_CAKE', 7, 'Bizcocho', 'BAKING_POWDER', 8, 'g', ''), ('CARROT_CAKE', 8, 'Bizcocho', 'BAKING_SODA', 6, 'g', ''),
    ('CARROT_CAKE', 9, 'Bizcocho', 'CINNAMON_GROUND', 6, 'g', ''), ('CARROT_CAKE', 10, 'Bizcocho', 'NUTMEG', 1, 'g', ''),
    ('CARROT_CAKE', 11, 'Bizcocho', 'SALT', 3, 'g', ''), ('CARROT_CAKE', 12, 'Extras', 'WALNUT', 120, 'g', '*'),
    ('CARROT_CAKE', 13, 'Extras', 'RAISINS', 100, 'g', '*'), ('CARROT_CAKE', 14, 'Betún', 'CREAM_CHEESE', 450, 'g', ''),
    ('CARROT_CAKE', 15, 'Betún', 'BUTTER_UNSALTED', 120, 'g', ''), ('CARROT_CAKE', 16, 'Betún', 'SUGAR_POWDERED', 350, 'g', ''),
    ('CARROT_CAKE', 17, 'Betún', 'VANILLA_EXTRACT', 5, 'ml', ''), ('CARROT_CAKE', 18, 'Empaque', 'CAKE_BOARD_25', 1, 'pz', ''),
    ('CARROT_CAKE', 19, 'Empaque', 'CAKE_BOX_25', 1, 'pz', ''),

    ('BERRY_TART', 1, 'Masa sablée', 'WHEAT_FLOUR_AP', 250, 'g', ''), ('BERRY_TART', 2, 'Masa sablée', 'BUTTER_UNSALTED', 150, 'g', ''),
    ('BERRY_TART', 3, 'Masa sablée', 'SUGAR_POWDERED', 90, 'g', ''), ('BERRY_TART', 4, 'Masa sablée', 'ALMOND_FLOUR', 30, 'g', ''),
    ('BERRY_TART', 5, 'Masa sablée', 'EGG_WHOLE', 1, 'pz', ''), ('BERRY_TART', 6, 'Masa sablée', 'SALT', 2, 'g', ''),
    ('BERRY_TART', 7, 'Crema pastelera', 'MILK_WHOLE', 500, 'ml', ''), ('BERRY_TART', 8, 'Crema pastelera', 'EGG_YOLK', 5, 'pz', ''),
    ('BERRY_TART', 9, 'Crema pastelera', 'SUGAR_WHITE', 120, 'g', ''), ('BERRY_TART', 10, 'Crema pastelera', 'CORNSTARCH', 45, 'g', ''),
    ('BERRY_TART', 11, 'Crema pastelera', 'BUTTER_UNSALTED', 30, 'g', ''), ('BERRY_TART', 12, 'Crema pastelera', 'VANILLA_BEAN', 1, 'pz', ''),
    ('BERRY_TART', 13, 'Fruta', 'STRAWBERRY', 250, 'g', ''), ('BERRY_TART', 14, 'Fruta', 'RASPBERRY', 170, 'g', '+'),
    ('BERRY_TART', 15, 'Fruta', 'BLUEBERRY', 170, 'g', '+'), ('BERRY_TART', 16, 'Brillo', 'FRUIT_JAM', 80, 'g', ''),
    ('BERRY_TART', 17, 'Brillo', 'GELATIN_POWDER', 3, 'g', '*');

INSERT INTO recipes (id, code, owner_id, name, category_id, difficulty, description, process_html, storage_instructions, yield_quantity,
                     yield_unit, prep_minutes, bake_minutes, cool_minutes, oven_temperature_c, shelf_life_days, status,
                     target_margin_percent, waste_percent, cost_status, version, created_at)
SELECT md5('cronos-recipe:' || s.code)::uuid, s.code, NULL, s.name,
       (SELECT c.id FROM categories c WHERE c.scope = 'SYSTEM' AND c.type = 'PRODUCT' AND c.code = s.category),
       s.difficulty, s.description,
       '<ol>' || (SELECT string_agg('<li>' || step || '</li>', '' ORDER BY n) FROM unnest(s.steps) WITH ORDINALITY AS t(step, n)) || '</ol>',
       CASE WHEN s.refrigerated THEN 'Refrigerar 2–4 °C en recipiente hermético' ELSE 'Recipiente hermético a temperatura ambiente' END,
       s.yield_quantity, s.yield_unit, s.prep, nullif(s.bake, 0), s.cool, s.oven, s.shelf_life, 'ACTIVE', s.margin, 3, 'STALE', 0, now()
FROM seed_recipes s
WHERE NOT EXISTS (SELECT 1 FROM recipes r WHERE r.owner_id IS NULL AND r.code = s.code AND r.deleted_at IS NULL);

INSERT INTO recipe_lines (id, recipe_id, ingredient_id, section, quantity, unit_id, optional, quote_selectable, display_order)
SELECT md5('cronos-line:' || l.recipe || ':' || l.position)::uuid, md5('cronos-recipe:' || l.recipe)::uuid, i.id, l.section, l.quantity,
       u.id, l.flag = '*', l.flag IN ('*', '+'), l.position
FROM seed_recipe_lines l
JOIN recipes r ON r.id = md5('cronos-recipe:' || l.recipe)::uuid
JOIN ingredients i ON i.owner_id IS NULL AND i.code = l.ingredient
JOIN measurement_units u ON u.code_identity = l.unit AND u.deleted_at IS NULL
ON CONFLICT (id) DO NOTHING;

INSERT INTO kitchen_jobs (kind, payload)
SELECT 'RECALCULATE_RECIPES',
       jsonb_build_object('ingredientId', NULL::uuid, 'recipeIds', jsonb_agg(r.id), 'reasonKey', 'kitchen.revision.seeded',
                          'reasonArgs', '[]'::jsonb, 'actor', NULL::uuid)
FROM recipes r
WHERE r.owner_id IS NULL AND r.deleted_at IS NULL AND r.cost_calculated_at IS NULL
HAVING count(*) > 0;
