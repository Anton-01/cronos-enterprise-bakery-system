-- Domain events, transactional outbox and traceability (auth-quotes-calendar-notifications §8).
-- Every side effect (email, push, SSE, cross-module projections) leaves the process through
-- outbox_messages, written in the same transaction as the business change.

CREATE TABLE outbox_messages (
    id             UUID PRIMARY KEY,                           -- UUIDv7: time-ordered, also the dispatch order
    type           VARCHAR(64)   NOT NULL,                      -- e.g. quote.accepted
    aggregate_type VARCHAR(32)   NOT NULL,
    aggregate_id   UUID          NOT NULL,
    payload        JSONB         NOT NULL,                      -- @Sensitive components are encrypted (enc: prefix)
    correlation_id UUID          NOT NULL,                      -- the HTTP request (or job run) that started the chain
    causation_id   UUID,                                        -- the event whose handler published this one
    trace_id       VARCHAR(64),
    actor_id       UUID,
    occurred_at    TIMESTAMPTZ   NOT NULL,
    available_at   TIMESTAMPTZ   NOT NULL,                      -- next attempt; pushed forward while a relay holds the lease
    status         VARCHAR(12)   NOT NULL DEFAULT 'PENDING',
    attempts       INT           NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    dispatched_at  TIMESTAMPTZ,
    dedupe_key     VARCHAR(160),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'DISPATCHED', 'FAILED', 'DEAD')),
    CONSTRAINT ux_outbox_dedupe UNIQUE (dedupe_key)
);
CREATE INDEX ix_outbox_pending ON outbox_messages (available_at, id) WHERE status = 'PENDING';
CREATE INDEX ix_outbox_aggregate ON outbox_messages (aggregate_type, aggregate_id, id) WHERE status = 'PENDING';
CREATE INDEX ix_outbox_aggregate_all ON outbox_messages (aggregate_type, aggregate_id, occurred_at DESC);
CREATE INDEX ix_outbox_correlation ON outbox_messages (correlation_id);
CREATE INDEX ix_outbox_causation ON outbox_messages (causation_id) WHERE causation_id IS NOT NULL;
CREATE INDEX ix_outbox_type_time ON outbox_messages (type, occurred_at DESC);
CREATE INDEX ix_outbox_status_time ON outbox_messages (status, occurred_at DESC);

-- A handler records the events it already applied in the same transaction as its effect: a
-- redelivered event hits the primary key and is acknowledged without running twice.
CREATE TABLE processed_events (
    handler_name VARCHAR(80) NOT NULL,
    event_id     UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (handler_name, event_id)
);
CREATE INDEX ix_processed_events_event ON processed_events (event_id);

-- One row per email the dispatcher tried to send (support trail; the address is only stored hashed).
CREATE TABLE mail_messages (
    id           UUID PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,                 -- the mail.requested outbox message
    template_key VARCHAR(64)  NOT NULL,
    locale       VARCHAR(5)   NOT NULL,
    to_hash      CHAR(64)     NOT NULL,
    status       VARCHAR(12)  NOT NULL,
    provider_id  VARCHAR(160),
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(1000),
    created_at   TIMESTAMPTZ  NOT NULL,
    sent_at      TIMESTAMPTZ,
    trace_id     VARCHAR(64),
    CONSTRAINT ck_mail_messages_status CHECK (status IN ('PENDING', 'SENT', 'RETRYING', 'FAILED'))
);
CREATE INDEX ix_mail_messages_status_time ON mail_messages (status, created_at DESC);
