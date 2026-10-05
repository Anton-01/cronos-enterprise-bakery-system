package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.access.AccessGuards;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.user.api.LoginAttempt;
import com.ninsky.cronos.iam.user.api.SessionsRevoked;
import com.ninsky.cronos.iam.user.api.UserSession;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Live sessions and sign-in history of a user (spec §3.8). */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final NamedParameterJdbcTemplate jdbc;
    private final UserReadRepository users;
    private final AccessGuards guards;
    private final SessionRevoker revoker;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final MessageSource messages;
    private final Clock clock;

    /** Only active, unexpired sessions, newest activity first. */
    @Transactional(readOnly = true)
    public List<UserSession> sessions(UUID userId) {
        target(userId, false);
        return jdbc.query("""
                        SELECT id, ip_address, browser, operating_system, device, location, created_at, last_activity_at, expires_at
                        FROM user_sessions WHERE user_id = :userId AND is_active AND terminated_at IS NULL AND expires_at > :now
                        ORDER BY coalesce(last_activity_at, created_at) DESC""",
                new MapSqlParameterSource("userId", userId).addValue("now", TenantTime.nowLocal(clock)),
                (rs, i) -> new UserSession(rs.getObject("id", UUID.class), rs.getString("ip_address"), rs.getString("browser"),
                        rs.getString("operating_system"), rs.getString("device"), rs.getString("location"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("last_activity_at")),
                        instant(rs.getTimestamp("expires_at"))));
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        UserRow row = target(userId, true);
        var params = params(userId).addValue("sessionId", sessionId);
        int rows = jdbc.update("""
                UPDATE user_sessions SET is_active = FALSE, terminated_at = :now, termination_reason = 'ADMIN_REVOKED'
                WHERE id = :sessionId AND user_id = :userId AND is_active""", params);
        if (rows == 0) {
            throw ApiException.notFound("iam.session.notFound");
        }
        jdbc.update("UPDATE refresh_tokens SET revoked = TRUE, revoked_at = :now WHERE session_id = :sessionId AND NOT revoked", params);
        revoker.blacklistSession(sessionId);
        recorder.record(AuditEvent.of(AuditAction.USER_SESSION_REVOKED, AuditTargets.USER, userId, row.displayName())
                .params(Map.of("sessionId", sessionId.toString())).build());
    }

    @Transactional
    public SessionsRevoked revokeAll(UUID userId) {
        UserRow row = target(userId, true);
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM user_sessions WHERE user_id = :userId AND is_active AND expires_at > :now""",
                params(userId), Integer.class);
        revoker.revokeAll(userId, "ADMIN_REVOKED");
        int revoked = Optional.ofNullable(active).orElse(0);
        recorder.record(AuditEvent.of(AuditAction.USER_SESSIONS_REVOKED, AuditTargets.USER, userId, row.displayName())
                .params(Map.of("revoked", revoked)).build());
        return new SessionsRevoked(revoked);
    }

    /** Newest first; the failure reason is generic and localised (never which credential was wrong). */
    @Transactional(readOnly = true)
    public CatalogPage<LoginAttempt> loginHistory(UUID userId, Integer page, Integer size, Locale locale) {
        target(userId, false);
        PageQuery query = PageQuery.of(page, size, null, Map.of("occurredAt", "login_at"), "occurredAt,desc");
        var params = new MapSqlParameterSource("userId", userId).addValue("limit", query.size()).addValue("offset", query.offset());
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT count(*) FROM login_history WHERE user_id = :userId", params, Long.class)).orElse(0L);
        List<LoginAttempt> rows = jdbc.query("""
                        SELECT id, login_at, coalesce(outcome, CASE WHEN successful THEN 'SUCCESS' ELSE 'FAILURE' END) AS outcome,
                               ip_address, browser, operating_system, location
                        FROM login_history WHERE user_id = :userId ORDER BY login_at DESC, id LIMIT :limit OFFSET :offset""",
                params, (rs, i) -> {
                    String outcome = rs.getString("outcome");
                    return new LoginAttempt(rs.getObject("id", UUID.class), instant(rs.getTimestamp("login_at")), outcome,
                            "SUCCESS".equals(outcome) ? null : messages.getMessage("iam.loginHistory." + outcome, null,
                                    outcome, locale),
                            rs.getString("ip_address"), rs.getString("browser"), rs.getString("operating_system"),
                            rs.getString("location"));
                });
        return CatalogPage.of(rows, query, total);
    }

    /** Never the caller's own account; root protection applies to changes only. */
    private UserRow target(UUID userId, boolean modify) {
        Actor actor = actors.require();
        guards.requireNotSelf(actor, userId, null);
        UserRow row = users.find(userId).orElseThrow(() -> ApiException.notFound("iam.user.notFound"));
        if (modify) {
            guards.requireCanModify(actor, List.of(userId));
        }
        return row;
    }

    private MapSqlParameterSource params(UUID userId) {
        LocalDateTime now = TenantTime.nowLocal(clock);
        return new MapSqlParameterSource("userId", userId).addValue("now", now);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : TenantTime.toInstant(value.toLocalDateTime());
    }
}
