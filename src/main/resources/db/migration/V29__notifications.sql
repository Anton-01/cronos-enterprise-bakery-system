-- In-app notifications, preferences, Web Push and the SSE ticket store (auth-quotes-calendar-notifications §6, §9).
-- Adapted to this schema: accounts live in users; push_deliveries also records the outbox event that produced it
-- (event_id) for the event trail, unique per subscription so a retried event never pushes twice; rendered texts are
-- encrypted like the quote's client fields.

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,                 -- UUIDv7: time-ordered, also the SSE event id
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            VARCHAR(32)   NOT NULL,
    category        VARCHAR(16)   NOT NULL,
    severity        VARCHAR(8)    NOT NULL CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'CRITICAL')),
    title           TEXT          NOT NULL,                -- rendered at creation, encrypted (may name a client)
    body            TEXT          NOT NULL,
    link            VARCHAR(300) CHECK (link IS NULL OR link LIKE '/%'),
    subject_type    VARCHAR(24),
    subject_id      UUID,
    actor_name      TEXT,                                   -- encrypted
    source_event_id UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    read_at         TIMESTAMPTZ,
    CONSTRAINT ux_notifications_source UNIQUE (user_id, source_event_id, type)
);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX ix_notifications_user_time ON notifications (user_id, id DESC);
CREATE INDEX ix_notifications_created ON notifications (created_at);

CREATE TABLE notification_preferences (
    user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category VARCHAR(16) NOT NULL,
    in_app   BOOLEAN     NOT NULL,
    push     BOOLEAN     NOT NULL,
    email    BOOLEAN     NOT NULL,
    PRIMARY KEY (user_id, category)
);

CREATE TABLE notification_settings (
    user_id                  UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    quiet_enabled            BOOLEAN     NOT NULL DEFAULT FALSE,
    quiet_start              TIME,
    quiet_end                TIME,
    default_reminder_minutes INT         NOT NULL DEFAULT 1440 CHECK (default_reminder_minutes BETWEEN 0 AND 10080),
    timezone                 VARCHAR(64) NOT NULL DEFAULT 'America/Mexico_City',
    version                  BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE push_subscriptions (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint        TEXT         NOT NULL UNIQUE,
    endpoint_hash   CHAR(64)     NOT NULL,
    p256dh          VARCHAR(255) NOT NULL,
    auth            VARCHAR(64)  NOT NULL,
    device_label    VARCHAR(80),
    browser         VARCHAR(40),
    os              VARCHAR(40),
    user_agent      VARCHAR(512),
    locale          VARCHAR(5),
    created_at      TIMESTAMPTZ  NOT NULL,
    last_success_at TIMESTAMPTZ,
    failure_count   INT          NOT NULL DEFAULT 0,
    revoked_at      TIMESTAMPTZ
);
CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id) WHERE revoked_at IS NULL;

CREATE TABLE push_deliveries (
    id              UUID PRIMARY KEY,
    subscription_id UUID         NOT NULL REFERENCES push_subscriptions (id) ON DELETE CASCADE,
    notification_id UUID,
    event_id        UUID         NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('DELIVERED', 'RETRYING', 'FAILED', 'REVOKED')),
    http_status     INT,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,
    delivered_at    TIMESTAMPTZ,
    trace_id        VARCHAR(64),
    CONSTRAINT ux_push_deliveries_event UNIQUE (event_id, subscription_id)
);
CREATE INDEX ix_push_deliveries_created ON push_deliveries (created_at);

-- Single-use, 60-second tickets that authenticate an EventSource (which cannot send headers); stored hashed.
CREATE TABLE sse_tickets (
    ticket_hash CHAR(64) PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id  UUID,
    expires_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_sse_tickets_expiry ON sse_tickets (expires_at);
