package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.access.AccessVersions;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The one place that moves a user between statuses: status columns, the legacy
 * enabled/account_non_locked/locked_until flags, version, access version and session revocation
 * (always when leaving ACTIVE, spec §1.4.5).
 * Callers validate the transition and write the audit event.
 */
@Component
@RequiredArgsConstructor
public class UserStatusWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessVersions accessVersions;
    private final SessionRevoker sessionRevoker;
    private final Clock clock;

    public record Change(UserStatus status, StatusReason reason, String comment, Instant until, UUID changedBy) {
    }

    /** @return false when the row was not at {@code expectedVersion} (null skips the check) */
    @Transactional
    public boolean apply(UUID userId, Change change, Long expectedVersion) {
        Instant now = TenantTime.now(clock);
        boolean locked = change.status() == UserStatus.LOCKED;
        int rows = jdbc.update("""
                UPDATE users SET status = :status, status_reason = :reason, status_comment = :comment,
                       status_until = :until, status_changed_at = :now, status_changed_by = :changedBy,
                       enabled = :enabled, account_non_locked = :nonLocked, locked_until = :lockedUntil,
                       failed_login_attempts = CASE WHEN :status = 'ACTIVE' THEN 0 ELSE failed_login_attempts END,
                       version = version + 1, updated_at = :nowLocal, updated_by_id = coalesce(:changedBy, updated_by_id)
                WHERE id = :id AND (CAST(:expected AS BIGINT) IS NULL OR version = :expected)""",
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("status", change.status().name())
                        .addValue("reason", change.reason() == null ? null : change.reason().name())
                        .addValue("comment", change.comment())
                        .addValue("until", change.until() == null ? null : Timestamp.from(change.until()))
                        .addValue("now", Timestamp.from(now))
                        .addValue("nowLocal", TenantTime.toLocal(now))
                        .addValue("changedBy", change.changedBy())
                        .addValue("enabled", change.status() != UserStatus.SUSPENDED && change.status() != UserStatus.DEACTIVATED)
                        .addValue("nonLocked", !locked)
                        .addValue("lockedUntil", locked ? TenantTime.toLocal(change.until()) : null)
                        .addValue("expected", expectedVersion));
        if (rows == 0) {
            return false;
        }
        accessVersions.bump(List.of(userId));
        if (change.status() != UserStatus.ACTIVE && change.status() != UserStatus.PENDING_ACTIVATION) {
            sessionRevoker.revokeAll(userId, "STATUS_" + change.status().name());
        }
        return true;
    }
}
