-- Finance seed (spec §13.5–13.7).

INSERT INTO currencies (code, numeric_code, name, symbol, decimal_places, symbol_position, is_default) VALUES
    ('MXN', '484', 'Peso mexicano', '$', 2, 'BEFORE', TRUE),
    ('USD', '840', 'Dólar estadounidense', 'US$', 2, 'BEFORE', FALSE),
    ('EUR', '978', 'Euro', '€', 2, 'AFTER', FALSE);

-- Codes already stored on quotes or ingredient prices stay valid as ACTIVE catalog rows.
INSERT INTO currencies (code, numeric_code, name, symbol, decimal_places, symbol_position)
SELECT legacy.code, legacy.numeric_code, legacy.name, legacy.symbol, legacy.decimal_places, 'BEFORE'
FROM (VALUES ('COP', '170', 'Peso colombiano', 'COL$', 2),
             ('ARS', '032', 'Peso argentino', 'AR$', 2),
             ('CLP', '152', 'Peso chileno', 'CLP$', 0),
             ('PEN', '604', 'Sol peruano', 'S/', 2),
             ('GTQ', '320', 'Quetzal guatemalteco', 'Q', 2)) AS legacy (code, numeric_code, name, symbol, decimal_places)
WHERE EXISTS (SELECT 1 FROM quotes q WHERE upper(q.currency) = legacy.code)
   OR EXISTS (SELECT 1 FROM raw_materials r WHERE upper(r.currency) = legacy.code);

INSERT INTO tax_rates (code, name, description, factor_type, rate_percent, valid_from, valid_to, is_default) VALUES
    ('IVA_16', 'IVA general 16%', 'Tasa general nacional', 'TASA', 16.0000, DATE '2010-01-01', NULL, TRUE),
    ('IVA_8_FRONTERA', 'IVA región fronteriza 8%', 'Estímulo fiscal región fronteriza (decreto vigente)', 'TASA', 8.0000,
     DATE '2019-01-01', DATE '2026-12-31', FALSE),
    ('IVA_0', 'IVA tasa 0%', 'Actos o actividades gravados a la tasa 0%', 'TASA', 0.0000, DATE '2010-01-01', NULL, FALSE),
    ('IVA_EXENTO', 'Exento de IVA', 'Actos o actividades exentos', 'EXENTO', NULL, DATE '2010-01-01', NULL, FALSE);

INSERT INTO finance_settings (id, prices_include_tax, rounding_mode) VALUES (1, FALSE, 'HALF_UP');

-- Link existing quotes to the preset matching their rate, when one does.
UPDATE quotes q SET tax_rate_id = t.id
FROM tax_rates t
WHERE t.factor_type = 'TASA' AND t.rate_percent = q.tax_rate AND t.code IN ('IVA_16', 'IVA_8_FRONTERA', 'IVA_0');

CREATE INDEX ix_raw_materials_currency ON raw_materials (currency);
