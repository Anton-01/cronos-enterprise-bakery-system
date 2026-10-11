-- Orders calendar (auth-quotes-calendar-notifications §5, §9). Adapted to this schema:
--  * client fields are encrypted at rest like the quote's (TEXT, AES-GCM), so search runs after decryption over the
--    bounded range query (≤ 62 days, ≤ 500 rows);
--  * plain btree range indexes instead of a GiST index (no btree_gist extension needed).
CREATE TABLE calendar_events (
    id                  UUID PRIMARY KEY,
    owner_id            UUID          NOT NULL REFERENCES users (id),
    title               VARCHAR(150)  NOT NULL,
    type                VARCHAR(16)   NOT NULL CHECK (type IN ('ORDER_DELIVERY', 'ORDER_PICKUP', 'PRODUCTION', 'TASTING', 'REMINDER', 'OTHER')),
    status              VARCHAR(16)   NOT NULL CHECK (status IN ('TENTATIVE', 'CONFIRMED', 'IN_PRODUCTION', 'READY', 'DELIVERED', 'CANCELLED')),
    start_at            TIMESTAMPTZ   NOT NULL,
    end_at              TIMESTAMPTZ   NOT NULL,
    all_day             BOOLEAN       NOT NULL DEFAULT FALSE,
    color               CHAR(7) CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),
    location            VARCHAR(300),
    client_name         TEXT,
    client_phone        TEXT,
    client_email        TEXT,
    notes               VARCHAR(2000),
    quote_id            UUID REFERENCES quotes (id),
    total_amount        NUMERIC(14, 2),
    currency            VARCHAR(3),
    reminders           SMALLINT[]    NOT NULL DEFAULT '{}',
    production_start_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_calendar_range CHECK (end_at > start_at),
    CONSTRAINT ck_calendar_production CHECK (production_start_at IS NULL OR production_start_at <= start_at)
);
CREATE INDEX ix_calendar_owner_start ON calendar_events (owner_id, start_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_calendar_owner_end ON calendar_events (owner_id, end_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_calendar_quote ON calendar_events (quote_id) WHERE quote_id IS NOT NULL;

CREATE TABLE calendar_event_recipes (
    id                   UUID PRIMARY KEY,
    event_id             UUID          NOT NULL REFERENCES calendar_events (id) ON DELETE CASCADE,
    recipe_id            UUID          NOT NULL REFERENCES recipes (id),
    quantity             NUMERIC(10, 2) NOT NULL CHECK (quantity > 0 AND quantity <= 10000),
    recipe_configuration JSONB,
    notes                VARCHAR(300),
    display_order        INT           NOT NULL
);
CREATE INDEX ix_calendar_event_recipes_event ON calendar_event_recipes (event_id, display_order);
CREATE INDEX ix_calendar_event_recipes_recipe ON calendar_event_recipes (recipe_id);

-- Materialised from the event by an outbox projection; the job publishes the due ones once (dedupe_key).
CREATE TABLE calendar_reminders (
    id             UUID PRIMARY KEY,
    event_id       UUID         NOT NULL REFERENCES calendar_events (id) ON DELETE CASCADE,
    kind           VARCHAR(16)  NOT NULL CHECK (kind IN ('REMINDER', 'PRODUCTION_START')),
    minutes_before INT,
    fire_at        TIMESTAMPTZ  NOT NULL,
    sent_at        TIMESTAMPTZ,
    dedupe_key     VARCHAR(80)  NOT NULL UNIQUE
);
CREATE INDEX ix_calendar_reminders_due ON calendar_reminders (fire_at) WHERE sent_at IS NULL;
CREATE INDEX ix_calendar_reminders_event ON calendar_reminders (event_id);

ALTER TABLE quotes ADD CONSTRAINT fk_quotes_calendar_event FOREIGN KEY (calendar_event_id) REFERENCES calendar_events (id);
