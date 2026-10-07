package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Status columns of {@code users} and the lifecycle scans over them. */
@Repository
@RequiredArgsConstructor
public class UserStatusCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Writes the status and the legacy flags; false when the row was not at {@code expectedVersion} (null skips the check). */
    public boolean apply(UUID userId, UserStatusWriter.Change change, Instant now, Long expectedVersion) {
        boolean locked = change.status() == UserStatus.LOCKED;
        return jdbc.update("""
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
                        .addValue("expected", expectedVersion)) > 0;
    }

    /** SUSPENDED/LOCKED users whose {@code status_until} has passed. */
    public List<UUID> expiredTemporaryStatus(Instant now) {
        return jdbc.queryForList("""
                        SELECT id FROM users WHERE status IN ('SUSPENDED', 'LOCKED') AND status_until IS NOT NULL
                        AND status_until <= :now""",
                new MapSqlParameterSource("now", Timestamp.from(now)), UUID.class);
    }

    /** Not-yet-deactivated users whose {@code access_expires_at} is reached. */
    public List<UUID> expiredAccess(LocalDate today) {
        return jdbc.queryForList("""
                        SELECT id FROM users WHERE status <> 'DEACTIVATED' AND access_expires_at IS NOT NULL
                        AND access_expires_at <= :today""",
                new MapSqlParameterSource("today", today), UUID.class);
    }
}
