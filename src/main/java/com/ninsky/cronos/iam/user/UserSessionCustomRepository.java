package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.user.api.IamUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** SQL over {@code user_sessions}, {@code refresh_tokens} and {@code login_history} for the admin views. */
@Repository
@RequiredArgsConstructor
public class UserSessionCustomRepository {

    /** API sort field → SQL for the login history. */
    public static final Map<String, String> HISTORY_SORTS = Map.of("occurredAt", "login_at");

    private final NamedParameterJdbcTemplate jdbc;

    /** A login row with its raw outcome code. */
    public record LoginRow(UUID id, Instant loginAt, String outcome, String ipAddress, String browser, String operatingSystem,
                           String location) {
    }

    /** Active, unexpired sessions, newest activity first. */
    public List<IamUserSession> activeSessions(UUID userId, LocalDateTime now) {
        return jdbc.query("""
                        SELECT id, ip_address, browser, operating_system, device, location, created_at, last_activity_at, expires_at
                        FROM user_sessions WHERE user_id = :userId AND is_active AND terminated_at IS NULL AND expires_at > :now
                        ORDER BY coalesce(last_activity_at, created_at) DESC""",
                new MapSqlParameterSource("userId", userId).addValue("now", now),
                (rs, i) -> new IamUserSession(rs.getObject("id", UUID.class), rs.getString("ip_address"), rs.getString("browser"),
                        rs.getString("operating_system"), rs.getString("device"), rs.getString("location"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("last_activity_at")),
                        instant(rs.getTimestamp("expires_at"))));
    }

    /** Ends one active session and its refresh tokens; false when no such active session. */
    public boolean revokeSession(UUID userId, UUID sessionId, LocalDateTime now) {
        var params = new MapSqlParameterSource("userId", userId).addValue("sessionId", sessionId).addValue("now", now);
        int rows = jdbc.update("""
                UPDATE user_sessions SET is_active = FALSE, terminated_at = :now, termination_reason = 'ADMIN_REVOKED'
                WHERE id = :sessionId AND user_id = :userId AND is_active""", params);
        if (rows == 0) {
            return false;
        }
        jdbc.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = :now WHERE session_id = :sessionId AND NOT revoked", params);
        return true;
    }

    public int countActive(UUID userId, LocalDateTime now) {
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT count(*) FROM user_sessions WHERE user_id = :userId AND is_active AND expires_at > :now""",
                new MapSqlParameterSource("userId", userId).addValue("now", now), Integer.class)).orElse(0);
    }

    public long countLogins(UUID userId) {
        return Optional.ofNullable(jdbc.queryForObject("SELECT count(*) FROM login_history WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), Long.class)).orElse(0L);
    }

    /** Newest first. */
    public List<LoginRow> logins(UUID userId, int limit, long offset) {
        return jdbc.query("""
                        SELECT id, login_at, coalesce(outcome, CASE WHEN successful THEN 'SUCCESS' ELSE 'FAILURE' END) AS outcome,
                               ip_address, browser, operating_system, location
                        FROM login_history WHERE user_id = :userId ORDER BY login_at DESC, id LIMIT :limit OFFSET :offset""",
                new MapSqlParameterSource("userId", userId).addValue("limit", limit).addValue("offset", offset),
                (rs, i) -> new LoginRow(rs.getObject("id", UUID.class), instant(rs.getTimestamp("login_at")), rs.getString("outcome"),
                        rs.getString("ip_address"), rs.getString("browser"), rs.getString("operating_system"), rs.getString("location")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : TenantTime.toInstant(value.toLocalDateTime());
    }
}
