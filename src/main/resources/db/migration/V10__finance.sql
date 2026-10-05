-- Finance settings (spec §9–§12): ISO 4217 currency catalog, IVA rates aligned with SAT CFDI 4.0,
-- one tenant default for each, calculation settings, and the per-quote pricing snapshot.
-- Fixed-width codes are VARCHAR + CHECK rather than CHAR(n) (CHAR blank-pads; Hibernate validate maps
-- String to VARCHAR), same convention as V6.

CREATE TABLE currencies (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(3)  NOT NULL UNIQUE,
    numeric_code    VARCHAR(3)  NOT NULL UNIQUE,
    name            VARCHAR(60) NOT NULL,
    symbol          VARCHAR(5)  NOT NULL,
    decimal_places  SMALLINT    NOT NULL,
    symbol_position VARCHAR(6)  NOT NULL,
    is_default      BOOLEAN     NOT NULL DEFAULT FALSE,
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID REFERENCES users (id),
    updated_at      TIMESTAMPTZ,
    updated_by      UUID REFERENCES users (id),
    CONSTRAINT ck_currencies_code CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_currencies_numeric_code CHECK (numeric_code ~ '^[0-9]{3}$'),
    CONSTRAINT ck_currencies_decimal_places CHECK (decimal_places BETWEEN 0 AND 4),
    CONSTRAINT ck_currencies_symbol_position CHECK (symbol_position IN ('BEFORE', 'AFTER')),
    CONSTRAINT ck_currencies_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_currencies_default_active CHECK (NOT is_default OR status = 'ACTIVE')
);
CREATE UNIQUE INDEX ux_currencies_name_ci ON currencies (lower(name));
-- Exactly one default: at most one row via this index, at least one via the service.
CREATE UNIQUE INDEX ux_currencies_single_default ON currencies (is_default) WHERE is_default;

CREATE TABLE tax_rates (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(30)  NOT NULL UNIQUE,
    name         VARCHAR(60)  NOT NULL,
    description  VARCHAR(250),
    sat_tax_code VARCHAR(3)   NOT NULL DEFAULT '002',
    factor_type  VARCHAR(6)   NOT NULL,
    rate_percent NUMERIC(7, 4),
    valid_from   DATE         NOT NULL,
    valid_to     DATE,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID REFERENCES users (id),
    updated_at   TIMESTAMPTZ,
    updated_by   UUID REFERENCES users (id),
    CONSTRAINT ck_tax_rates_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT ck_tax_rates_sat_tax_code CHECK (sat_tax_code = '002'),
    CONSTRAINT ck_tax_rates_factor_type CHECK (factor_type IN ('TASA', 'EXENTO')),
    CONSTRAINT ck_tax_rates_rate_range CHECK (rate_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_tax_rates_rate_by_factor CHECK ((factor_type = 'EXENTO' AND rate_percent IS NULL)
                                               OR (factor_type = 'TASA' AND rate_percent IS NOT NULL)),
    CONSTRAINT ck_tax_rates_validity CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_tax_rates_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_tax_rates_default_active CHECK (NOT is_default OR status = 'ACTIVE')
);
CREATE UNIQUE INDEX ux_tax_rates_name_ci ON tax_rates (lower(name));
CREATE UNIQUE INDEX ux_tax_rates_single_default ON tax_rates (is_default) WHERE is_default;

CREATE TABLE finance_settings (
    id                 SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    prices_include_tax BOOLEAN     NOT NULL DEFAULT FALSE,
    rounding_mode      VARCHAR(10) NOT NULL DEFAULT 'HALF_UP',
    version            BIGINT      NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ,
    updated_by         UUID REFERENCES users (id),
    CONSTRAINT ck_finance_settings_rounding CHECK (rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'UP', 'DOWN'))
);

-- Quote snapshot (§11.4): recalculations use these, never the current defaults.
ALTER TABLE quotes
    ADD COLUMN currency_decimal_places SMALLINT,
    ADD COLUMN tax_rate_id             BIGINT REFERENCES tax_rates (id),
    ADD COLUMN tax_factor_type         VARCHAR(6),
    ADD COLUMN prices_include_tax      BOOLEAN,
    ADD COLUMN rounding_mode           VARCHAR(10),
    ADD CONSTRAINT ck_quotes_tax_factor_type CHECK (tax_factor_type IS NULL OR tax_factor_type IN ('TASA', 'EXENTO')),
    ADD CONSTRAINT ck_quotes_rounding_mode CHECK (rounding_mode IS NULL OR rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'UP', 'DOWN'));
-- Rates are stored with up to 4 decimals (SAT TasaOCuota precision).
ALTER TABLE quotes ALTER COLUMN tax_rate TYPE NUMERIC(7, 4);

UPDATE quotes SET currency_decimal_places = CASE WHEN currency = 'CLP' THEN 0 ELSE 2 END,
                  tax_factor_type = 'TASA', prices_include_tax = FALSE, rounding_mode = 'HALF_UP';
ALTER TABLE quotes
    ALTER COLUMN currency_decimal_places SET NOT NULL,
    ALTER COLUMN tax_factor_type SET NOT NULL,
    ALTER COLUMN prices_include_tax SET NOT NULL,
    ALTER COLUMN rounding_mode SET NOT NULL;
CREATE INDEX ix_quotes_tax_rate ON quotes (tax_rate_id) WHERE tax_rate_id IS NOT NULL;
CREATE INDEX ix_quotes_currency ON quotes (currency);
