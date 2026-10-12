package com.ninsky.cronos.identity.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Screen-lock state of server sessions ({@code user_sessions.locked_at}) and the remember-me flag. The JWT filter
 * asks {@link #isLocked} on every request, so answers are cached per instance for a few seconds; this instance's
 * writes evict immediately, other instances converge within the TTL.
 */
@Repository
public class SessionLockCustomRepository {

    static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final NamedParameterJdbcTemplate jdbc;
    private final Cache<UUID, Boolean> locked = Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).maximumSize(50_000).build();

    public SessionLockCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks an active session (idempotent); false when the session is gone or inactive. */
    public boolean lock(UUID sessionId, Instant now) {
        int rows = jdbc.update("UPDATE user_sessions SET locked_at = coalesce(locked_at, :now) WHERE id = :id AND is_active",
                new MapSqlParameterSource("id", sessionId).addValue("now", Timestamp.from(now)));
        locked.invalidate(sessionId);
        return rows == 1;
    }

    /** Clears the lock. */
    public void unlock(UUID sessionId) {
        jdbc.update("UPDATE user_sessions SET locked_at = NULL WHERE id = :id", Map.of("id", sessionId));
        locked.invalidate(sessionId);
    }

    /** True while the session's screen is locked. */
    public boolean isLocked(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        return locked.get(sessionId, id -> Boolean.TRUE.equals(jdbc.query(
                "SELECT locked_at IS NOT NULL FROM user_sessions WHERE id = :id", Map.of("id", id),
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE)));
    }

    /** Marks a new session as remember-me (longer refresh lifetime). */
    public void rememberMe(UUID sessionId, boolean rememberMe) {
        jdbc.update("UPDATE user_sessions SET remember_me = :remember WHERE id = :id",
                new MapSqlParameterSource("id", sessionId).addValue("remember", rememberMe));
    }

    /** Whether the session was opened with remember-me. */
    public boolean isRememberMe(UUID sessionId) {
        return Boolean.TRUE.equals(jdbc.query("SELECT remember_me FROM user_sessions WHERE id = :id", Map.of("id", sessionId),
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE));
    }
}
