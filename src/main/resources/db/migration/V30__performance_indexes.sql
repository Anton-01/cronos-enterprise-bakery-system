-- performance-latency P1-4: indexes the read paths still lacked. Already in place and not repeated here:
--   users: ux_users_username_lower (lower(username)), email_blind_index UNIQUE (V1)
--   refresh_tokens.token UNIQUE (SHA-256 digests since V26); user_sessions ix_user_sessions_user_active (V9)
--   notifications ix_notifications_user_time (user_id, id DESC), ix_notifications_user_unread (V29)
--   ingredient_i18n trigram on kitchen_fold(name), quotes (user_id, status, created_at DESC), calendar (owner_id, start_at)

-- Refresh / logout / revoke resolve the tokens of a session (revokeTokensBySessionId was 335 ms on Neon).
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_session ON refresh_tokens (session_id) WHERE session_id IS NOT NULL;

-- Unread badge: one GROUP BY category over the caller's unread rows, answered from the index alone.
CREATE INDEX IF NOT EXISTS ix_notifications_user_unread_category ON notifications (user_id, category) WHERE read_at IS NULL;

-- Recipe grid search (q, debounced 300 ms by the UI): LIKE '%term%' over the folded name and code.
CREATE INDEX IF NOT EXISTS ix_recipes_name_trgm ON recipes USING gin (kitchen_fold(name) gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_recipes_code_trgm ON recipes USING gin (kitchen_fold(code) gin_trgm_ops) WHERE deleted_at IS NULL;

-- Ingredient grid: own rows of a tenant, and code search.
CREATE INDEX IF NOT EXISTS ix_ingredients_code_trgm ON ingredients USING gin (kitchen_fold(code) gin_trgm_ops);

-- Category pickers are always filtered by scope and type (never an unfiltered scan).
CREATE INDEX IF NOT EXISTS ix_categories_scope_type ON categories (scope, type, id) WHERE status <> 'TRASHED';

-- Quotes grid without a status filter, newest first (the V27 index leads with status).
CREATE INDEX IF NOT EXISTS ix_quotes_owner_created ON quotes (user_id, created_at DESC, id) WHERE deleted_at IS NULL;
