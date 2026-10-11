-- Quote lifecycle (auth-quotes-calendar-notifications §4): explicit statuses, status history, accept-and-schedule,
-- soft delete. valid_until keeps its legacy TIMESTAMP (tenant wall time); new instants are TIMESTAMPTZ.

ALTER TABLE quotes DROP CONSTRAINT IF EXISTS quotes_status_check;
UPDATE quotes SET status = 'REVOKED' WHERE status = 'CANCELED';

ALTER TABLE quotes
    ADD COLUMN event_date        DATE,
    ADD COLUMN accepted_at       TIMESTAMPTZ,
    ADD COLUMN rejected_at       TIMESTAMPTZ,
    ADD COLUMN reject_reason     VARCHAR(32),
    ADD COLUMN calendar_event_id UUID,
    ADD COLUMN first_viewed_at   TIMESTAMPTZ,
    ADD COLUMN deleted_at        TIMESTAMPTZ,
    ADD CONSTRAINT ck_quotes_status CHECK (status IN ('DRAFT', 'SENT', 'VIEWED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'REVOKED')),
    ADD CONSTRAINT ck_quotes_reject_reason CHECK (reject_reason IS NULL OR reject_reason IN
        ('PRICE', 'DATE_UNAVAILABLE', 'CHOSE_COMPETITOR', 'NO_RESPONSE', 'CHANGED_PLANS', 'OTHER'));

-- A SENT quote whose link was already opened is VIEWED.
UPDATE quotes q SET status = 'VIEWED',
       first_viewed_at = (SELECT min(l.accessed_at) FROM quote_access_logs l WHERE l.quote_id = q.id) AT TIME ZONE 'America/Mexico_City'
WHERE q.status = 'SENT' AND EXISTS (SELECT 1 FROM quote_access_logs l WHERE l.quote_id = q.id);

CREATE INDEX ix_quotes_owner_status_created ON quotes (user_id, status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_quotes_open_valid_until ON quotes (valid_until) WHERE status IN ('SENT', 'VIEWED') AND deleted_at IS NULL;
CREATE INDEX ix_quote_access_logs_quote ON quote_access_logs (quote_id, accessed_at);

CREATE TABLE quote_status_history (
    id         UUID PRIMARY KEY,
    quote_id   UUID         NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    status     VARCHAR(16)  NOT NULL,
    actor_type VARCHAR(8)   NOT NULL CHECK (actor_type IN ('BAKER', 'CLIENT', 'SYSTEM')),
    actor_id   UUID,
    actor_name VARCHAR(150),
    detail     VARCHAR(64),
    comment    VARCHAR(500),
    changed_at TIMESTAMPTZ  NOT NULL,
    trace_id   VARCHAR(64)
);
CREATE INDEX ix_quote_history_quote ON quote_status_history (quote_id, changed_at);

-- Every existing quote gets the entry of its current status so the history is never empty.
INSERT INTO quote_status_history (id, quote_id, status, actor_type, actor_id, detail, changed_at)
SELECT md5('quote-history:' || q.id)::uuid, q.id, q.status, 'SYSTEM', NULL, 'MIGRATED',
       coalesce(q.updated_at, q.created_at) AT TIME ZONE 'America/Mexico_City'
FROM quotes q;
