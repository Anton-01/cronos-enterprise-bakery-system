-- Unit catalog hardening (unit_types / measurement_units) + immutable ledger of bulk data imports.
--
-- 1. unit_types.dimension becomes a closed vocabulary (UnitDimension: MASS, VOLUME, COUNT, LENGTH).
--    Conversions used to route on the literal strings 'MASA'/'VOLUMEN' while the UI suggested
--    'mass'/'volume': any row typed the "wrong" way silently made MASS <-> VOLUME conversions throw.
--    Known Spanish/English spellings are normalized; anything else aborts the migration with the
--    offending values listed, so nothing is guessed.
-- 2. Uniqueness moves from the application into the database, scoped to non-deleted rows (both
--    tables are soft-deleted; the old column-level UNIQUE(code_identity) also blocked reusing the
--    code of a deleted row). Constraints that a multi-row import may legitimately violate mid-flight
--    (swapping names, moving the base flag) are DEFERRABLE INITIALLY DEFERRED exclusion constraints,
--    checked at commit. Every check aborts with an explicit message if existing data violates it —
--    fix those rows by hand, then re-run.
-- 3. data_import_batches: one append-only row per .xlsx import attempt (validated, committed,
--    rejected or failed), with the file's SHA-256 and the full row-level report.
-- 4. MANAGE_CATALOGS permission: grants catalog maintenance to non-super-admin roles.

-- ---------------------------------------------------------------------------------------------
-- 1. Dimension vocabulary
-- ---------------------------------------------------------------------------------------------
UPDATE unit_types
SET dimension = CASE upper(btrim(dimension))
                    WHEN 'MASS' THEN 'MASS'
                    WHEN 'MASA' THEN 'MASS'
                    WHEN 'PESO' THEN 'MASS'
                    WHEN 'WEIGHT' THEN 'MASS'
                    WHEN 'VOLUME' THEN 'VOLUME'
                    WHEN 'VOLUMEN' THEN 'VOLUME'
                    WHEN 'COUNT' THEN 'COUNT'
                    WHEN 'CONTEO' THEN 'COUNT'
                    WHEN 'PIEZA' THEN 'COUNT'
                    WHEN 'PIEZAS' THEN 'COUNT'
                    WHEN 'UNIDAD' THEN 'COUNT'
                    WHEN 'UNIDADES' THEN 'COUNT'
                    WHEN 'LENGTH' THEN 'LENGTH'
                    WHEN 'LONGITUD' THEN 'LENGTH'
                    ELSE dimension
                END;

DO $$
DECLARE
    unknown TEXT;
BEGIN
    SELECT string_agg(DISTINCT format('%s (id %s)', dimension, id), ', ')
    INTO unknown
    FROM unit_types
    WHERE dimension NOT IN ('MASS', 'VOLUME', 'COUNT', 'LENGTH');

    IF unknown IS NOT NULL THEN
        RAISE EXCEPTION 'V8: unit_types.dimension has values outside MASS/VOLUME/COUNT/LENGTH: %. Map them by hand and re-run.', unknown;
    END IF;
END $$;

ALTER TABLE unit_types
    ADD CONSTRAINT chk_unit_types_dimension CHECK (dimension IN ('MASS', 'VOLUME', 'COUNT', 'LENGTH'));

-- ---------------------------------------------------------------------------------------------
-- 2a. unit_types uniqueness
-- ---------------------------------------------------------------------------------------------
DO $$
DECLARE
    clash TEXT;
BEGIN
    SELECT string_agg(format('code "%s" x%s', code, n), ', ') INTO clash
    FROM (SELECT lower(code_identity) AS code, count(*) AS n FROM unit_types
          WHERE deleted_at IS NULL GROUP BY lower(code_identity) HAVING count(*) > 1) d;
    IF clash IS NOT NULL THEN
        RAISE EXCEPTION 'V8: duplicate unit_types codes (case-insensitive): %', clash;
    END IF;

    SELECT string_agg(format('name "%s" x%s', name, n), ', ') INTO clash
    FROM (SELECT lower(name) AS name, count(*) AS n FROM unit_types
          WHERE deleted_at IS NULL GROUP BY lower(name) HAVING count(*) > 1) d;
    IF clash IS NOT NULL THEN
        RAISE EXCEPTION 'V8: duplicate unit_types names (case-insensitive): %', clash;
    END IF;

    SELECT string_agg(format('%s x%s', dimension, n), ', ') INTO clash
    FROM (SELECT dimension, count(*) AS n FROM unit_types
          WHERE deleted_at IS NULL GROUP BY dimension HAVING count(*) > 1) d;
    IF clash IS NOT NULL THEN
        RAISE EXCEPTION 'V8: more than one unit type per dimension: %. Every unit of a dimension must share one base unit; merge them first.', clash;
    END IF;
END $$;

ALTER TABLE unit_types DROP CONSTRAINT IF EXISTS unit_types_code_identity_key;

CREATE UNIQUE INDEX ux_unit_types_code_lower ON unit_types (lower(code_identity)) WHERE deleted_at IS NULL;

ALTER TABLE unit_types
    ADD CONSTRAINT ex_unit_types_name_lower
        EXCLUDE USING btree (lower(name) WITH =) WHERE (deleted_at IS NULL) DEFERRABLE INITIALLY DEFERRED;

-- One unit type per dimension: same-dimension conversions are linear only if all those units
-- share one base unit.
ALTER TABLE unit_types
    ADD CONSTRAINT ex_unit_types_dimension
        EXCLUDE USING btree (dimension WITH =) WHERE (deleted_at IS NULL) DEFERRABLE INITIALLY DEFERRED;

-- ---------------------------------------------------------------------------------------------
-- 2b. measurement_units integrity
-- ---------------------------------------------------------------------------------------------
DO $$
DECLARE
    clash TEXT;
BEGIN
    SELECT string_agg(format('%s (id %s) = %s', code_identity, id, multiplier_to_base), ', ') INTO clash
    FROM measurement_units WHERE multiplier_to_base <= 0;
    IF clash IS NOT NULL THEN
        RAISE EXCEPTION 'V8: measurement_units with a non-positive multiplier_to_base (division by zero in conversions): %', clash;
    END IF;

    SELECT string_agg(format('%s (id %s) = %s', code_identity, id, multiplier_to_base), ', ') INTO clash
    FROM measurement_units WHERE is_base_unit AND multiplier_to_base <> 1;
    IF clash IS NOT NULL THEN
        RAISE EXCEPTION 'V8: base units must have multiplier_to_base = 1: %', clash;
    END IF;

    SELECT string_agg(format('name "%s" x%s', name, n), ', ') INTO clash
    FROM (SELECT lower(name) AS name, count(*) AS n FROM measurement_units
          WHERE deleted_at IS NULL GROUP BY lower(name) HAVING count(*) > 1) d;
    IF clash IS NOT NULL THEN
        RAISE EXCEPTION 'V8: duplicate measurement_units names (case-insensitive): %', clash;
    END IF;

    SELECT string_agg(format('unit_type_id %s x%s', unit_type_id, n), ', ') INTO clash
    FROM (SELECT unit_type_id, count(*) AS n FROM measurement_units
          WHERE is_base_unit AND deleted_at IS NULL GROUP BY unit_type_id HAVING count(*) > 1) d;
    IF clash IS NOT NULL THEN
        RAISE EXCEPTION 'V8: more than one base unit per unit type: %', clash;
    END IF;
END $$;

ALTER TABLE measurement_units
    ADD CONSTRAINT chk_measurement_units_multiplier_positive CHECK (multiplier_to_base > 0);

ALTER TABLE measurement_units
    ADD CONSTRAINT chk_measurement_units_base_multiplier CHECK (NOT is_base_unit OR multiplier_to_base = 1);

-- Codes stay case-sensitive on purpose: 'T' (tablespoon) and 't' (teaspoon) are distinct units.
ALTER TABLE measurement_units DROP CONSTRAINT IF EXISTS measurement_units_code_identity_key;

CREATE UNIQUE INDEX ux_measurement_units_code ON measurement_units (code_identity) WHERE deleted_at IS NULL;

ALTER TABLE measurement_units
    ADD CONSTRAINT ex_measurement_units_name_lower
        EXCLUDE USING btree (lower(name) WITH =) WHERE (deleted_at IS NULL) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE measurement_units
    ADD CONSTRAINT ex_measurement_units_one_base_per_type
        EXCLUDE USING btree (unit_type_id WITH =) WHERE (is_base_unit AND deleted_at IS NULL) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX IF NOT EXISTS idx_measurement_units_unit_type_id ON measurement_units (unit_type_id);

-- Supporting indexes for MeasurementUnitUsagePort ("is this unit referenced?") and the density
-- rule lookup by ingredient. recipe_ingredients.unit_id has no FK in V1 — adding one is left out
-- deliberately: it would fail on any pre-existing orphan line and needs its own data clean-up.
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_unit_id ON recipe_ingredients (unit_id);
CREATE INDEX IF NOT EXISTS idx_raw_materials_purchase_unit_id ON raw_materials (purchase_unit_id);
CREATE INDEX IF NOT EXISTS idx_ingredient_conversions_ingredient_id ON ingredient_conversions (ingredient_id);
CREATE INDEX IF NOT EXISTS idx_ingredient_conversions_volume_unit_id ON ingredient_conversions (volume_unit_id);
CREATE INDEX IF NOT EXISTS idx_ingredient_conversions_mass_unit_id ON ingredient_conversions (mass_unit_id);

-- ---------------------------------------------------------------------------------------------
-- 3. Bulk import ledger
-- ---------------------------------------------------------------------------------------------
CREATE TABLE data_import_batches (
    id               UUID         NOT NULL PRIMARY KEY,
    resource         VARCHAR(40)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    dry_run          BOOLEAN      NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    file_size_bytes  BIGINT       NOT NULL,
    file_sha256      VARCHAR(64),
    total_rows       INTEGER      NOT NULL DEFAULT 0,
    created_count    INTEGER      NOT NULL DEFAULT 0,
    updated_count    INTEGER      NOT NULL DEFAULT 0,
    unchanged_count  INTEGER      NOT NULL DEFAULT 0,
    rejected_rows    INTEGER      NOT NULL DEFAULT 0,
    error_count      INTEGER      NOT NULL DEFAULT 0,
    warning_count    INTEGER      NOT NULL DEFAULT 0,
    actor_user_id    UUID         NOT NULL,
    actor_username   VARCHAR(100) NOT NULL,
    trace_id         VARCHAR(64),
    started_at       TIMESTAMP(6) NOT NULL,
    finished_at      TIMESTAMP(6) NOT NULL,
    report           JSONB        NOT NULL,
    CONSTRAINT chk_data_import_batches_resource CHECK (resource IN ('UNIT_TYPE', 'MEASUREMENT_UNIT')),
    CONSTRAINT chk_data_import_batches_status CHECK (status IN ('VALIDATED', 'COMMITTED', 'REJECTED', 'FAILED')),
    CONSTRAINT chk_data_import_batches_sha256 CHECK (file_sha256 IS NULL OR file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_data_import_batches_counts CHECK (
        total_rows >= 0 AND created_count >= 0 AND updated_count >= 0 AND unchanged_count >= 0
        AND rejected_rows >= 0 AND error_count >= 0 AND warning_count >= 0),
    CONSTRAINT chk_data_import_batches_window CHECK (finished_at >= started_at)
);

CREATE INDEX idx_data_import_batches_resource_started ON data_import_batches (resource, started_at DESC);
CREATE INDEX idx_data_import_batches_sha256 ON data_import_batches (resource, file_sha256) WHERE status = 'COMMITTED';
CREATE INDEX idx_data_import_batches_actor ON data_import_batches (actor_user_id);
CREATE INDEX idx_data_import_batches_trace_id ON data_import_batches (trace_id);

-- Same structural immutability as audit_log (V4): the ledger is append-only at the database level.
CREATE OR REPLACE FUNCTION prevent_data_import_batch_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'data_import_batches is immutable: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER data_import_batches_immutable
    BEFORE UPDATE OR DELETE ON data_import_batches
    FOR EACH ROW EXECUTE FUNCTION prevent_data_import_batch_modification();

-- ---------------------------------------------------------------------------------------------
-- 4. Permission for catalog maintenance (SUPER_ADMIN is always allowed; grant this to other roles
--    through the admin roles API).
-- ---------------------------------------------------------------------------------------------
INSERT INTO permissions (name, description, resource, action, created_at, created_by)
VALUES ('MANAGE_CATALOGS', 'Gestión de catálogos maestros (tipos de unidad, unidades de medida)', 'CATALOGS', 'ALL', now(), 'flyway')
ON CONFLICT (name) DO NOTHING;
