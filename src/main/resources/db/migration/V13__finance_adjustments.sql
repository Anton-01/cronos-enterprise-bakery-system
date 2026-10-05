-- Quote amounts are rounded to the snapshot currency's decimal places, which the catalog allows
-- up to 4 (spec §9.2, §11.3). At scale 2 PostgreSQL would silently re-round amounts of 3- and
-- 4-decimal currencies (e.g. KWD, CLF). Widening is lossless for existing rows.
ALTER TABLE quotes
    ALTER COLUMN subtotal TYPE NUMERIC(17, 4),
    ALTER COLUMN tax_amount TYPE NUMERIC(17, 4),
    ALTER COLUMN total TYPE NUMERIC(17, 4),
    ALTER COLUMN delivery_fee TYPE NUMERIC(17, 4),
    ALTER COLUMN extra_fee TYPE NUMERIC(17, 4);

ALTER TABLE quote_items
    ALTER COLUMN unit_price TYPE NUMERIC(17, 4),
    ALTER COLUMN subtotal TYPE NUMERIC(17, 4);
