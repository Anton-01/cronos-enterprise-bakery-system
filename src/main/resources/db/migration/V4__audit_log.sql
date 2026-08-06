-- Immutable audit ledger for admin/security actions (who did what, to whom, when).
-- Immutability is enforced structurally, not just by app convention: the trigger below
-- unconditionally rejects UPDATE/DELETE at the database level.

CREATE TABLE audit_log (
    id                 BIGSERIAL PRIMARY KEY,
    actor_user_id      UUID,
    actor_username     VARCHAR(100),
    action             VARCHAR(50) NOT NULL,
    target_type        VARCHAR(50) NOT NULL,
    target_id          VARCHAR(100),
    details            TEXT,
    ip_address         VARCHAR(45),
    user_agent         VARCHAR(500),
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_actor_user_id ON audit_log(actor_user_id);
CREATE INDEX idx_audit_log_target ON audit_log(target_type, target_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is immutable: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_modification();
