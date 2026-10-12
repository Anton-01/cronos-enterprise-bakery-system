package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.application.response.auth.LoginHistoryResponse;
import com.ninsky.cronos.application.response.auth.UserSessionResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** "My security" screens in plain SQL: one statement each, mapped straight to the response records. */
@Repository
public class UserSecurityCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public UserSecurityCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Active sessions of {@code userId}, most recently used first; the one matching the caller's IP and agent is flagged. */
    public List<UserSessionResponse> activeSessions(UUID userId, String currentIp, String currentUserAgent) {
        return jdbc.query("""
                        SELECT id, ip_address, user_agent, browser, operating_system, device, location, last_activity_at, is_active
                        FROM user_sessions WHERE user_id = :user AND is_active ORDER BY last_activity_at DESC NULLS LAST""",
                new MapSqlParameterSource("user", userId),
                (rs, row) -> UserSessionResponse.builder()
                        .id(rs.getObject("id", UUID.class))
                        .ipAddress(rs.getString("ip_address"))
                        .userAgent(rs.getString("user_agent"))
                        .browser(rs.getString("browser"))
                        .os(rs.getString("operating_system"))
                        .device(rs.getString("device"))
                        .location(rs.getString("location"))
                        .lastActivityAt(local(rs.getTimestamp("last_activity_at")))
                        .isActive(rs.getBoolean("is_active"))
                        .isCurrentSession(Objects.equals(rs.getString("ip_address"), currentIp)
                                && Objects.equals(rs.getString("user_agent"), currentUserAgent))
                        .build());
    }

    /** The latest {@code limit} sign-in attempts of {@code userId}, newest first. */
    public List<LoginHistoryResponse> loginHistory(UUID userId, int limit) {
        return jdbc.query("""
                        SELECT ip_address, user_agent, status, failure_reason, created_at FROM login_history
                        WHERE user_id = :user ORDER BY created_at DESC LIMIT :limit""",
                new MapSqlParameterSource("user", userId).addValue("limit", limit),
                (rs, row) -> LoginHistoryResponse.builder()
                        .ipAddress(rs.getString("ip_address"))
                        .userAgent(rs.getString("user_agent"))
                        .status(rs.getString("status"))
                        .failureReason(rs.getString("failure_reason"))
                        .createdAt(local(rs.getTimestamp("created_at")))
                        .build());
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
