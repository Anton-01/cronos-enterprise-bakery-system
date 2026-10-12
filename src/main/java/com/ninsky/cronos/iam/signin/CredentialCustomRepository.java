package com.ninsky.cronos.iam.signin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Password columns of {@code users} and {@code password_history}. */
@Repository
@RequiredArgsConstructor
public class CredentialCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Stores the hash, clears the forced change and failed attempts, appends history. */
    public void storePassword(UUID userId, String hash, LocalDateTime now, boolean emailVerified) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", userId).addValue("hash", hash).addValue("now", now).addValue("verified", emailVerified);
        jdbc.update("""
                UPDATE users SET password = :hash, password_changed_at = :now, password_needs_change = FALSE,
                       credentials_non_expired = TRUE, failed_login_attempts = 0,
                       email_verified = email_verified OR :verified, version = version + 1, updated_at = :now
                WHERE id = :id""", params);
        jdbc.update("INSERT INTO password_history (id, user_id, password_hash, changed_at) VALUES (:historyId, :id, :hash, :now)",
                params.addValue("historyId", UUID.randomUUID()));
    }

    /** PENDING_ACTIVATION → ACTIVE; false when the user was not pending. */
    public boolean activateIfPending(UUID userId) {
        return jdbc.update("""
                UPDATE users SET status = 'ACTIVE', status_reason = NULL, status_comment = NULL, status_until = NULL,
                       status_changed_at = now(), status_changed_by = :id
                WHERE id = :id AND status = 'PENDING_ACTIVATION'""", new MapSqlParameterSource("id", userId)) > 0;
    }

    /** Counts one failed sign-in; returns the new count (null for an unknown user). */
    public Integer incrementFailedAttempts(UUID userId, LocalDateTime now) {
        return jdbc.queryForObject("""
                UPDATE users SET failed_login_attempts = failed_login_attempts + 1, last_failed_login = :now
                WHERE id = :id RETURNING failed_login_attempts""",
                new MapSqlParameterSource("id", userId).addValue("now", now), Integer.class);
    }

    /** Clears the failed-attempt counter and window and stamps the login time. */
    public void recordSuccessfulLogin(UUID userId, LocalDateTime now) {
        jdbc.update("UPDATE users SET failed_login_attempts = 0, first_failed_at = NULL, last_login_at = :now WHERE id = :id",
                new MapSqlParameterSource("id", userId).addValue("now", now));
    }

    /** Mirrors the Redis failure counter of the current window into the durable row (sign-in-failed consumer). */
    public void recordFailedAttempts(UUID userId, int attempts, LocalDateTime at) {
        jdbc.update("""
                        UPDATE users SET failed_login_attempts = :attempts, last_failed_login = :at,
                               first_failed_at = CASE WHEN :attempts = 1 OR first_failed_at IS NULL THEN now() ELSE first_failed_at END
                        WHERE id = :id""",
                new MapSqlParameterSource("id", userId).addValue("attempts", attempts).addValue("at", at));
    }

    /** Failure counters after one more failed sign-in. */
    public record FailureState(int attempts, int lockCount, Instant lastLockedAt) {
    }

    /**
     * Counts one failed sign-in inside a sliding window: a failure after the window started over restarts the count.
     * Returns empty for an unknown user.
     */
    public Optional<FailureState> registerFailure(UUID userId, Instant now, Instant windowStart, LocalDateTime nowLocal) {
        return jdbc.query("""
                        UPDATE users SET
                            failed_login_attempts = CASE WHEN first_failed_at IS NULL OR first_failed_at < :windowStart
                                                         THEN 1 ELSE failed_login_attempts + 1 END,
                            first_failed_at = CASE WHEN first_failed_at IS NULL OR first_failed_at < :windowStart
                                                   THEN :now ELSE first_failed_at END,
                            last_failed_login = :nowLocal
                        WHERE id = :id
                        RETURNING failed_login_attempts, lock_count, last_locked_at""",
                new MapSqlParameterSource("id", userId)
                        .addValue("now", Timestamp.from(now))
                        .addValue("windowStart", Timestamp.from(windowStart))
                        .addValue("nowLocal", nowLocal),
                (rs, row) -> new FailureState(rs.getInt("failed_login_attempts"), rs.getInt("lock_count"),
                        rs.getTimestamp("last_locked_at") == null ? null : rs.getTimestamp("last_locked_at").toInstant()))
                .stream().findFirst();
    }

    /** Stamps lock number {@code lockCount} and clears the failure window. */
    public void recordLock(UUID userId, int lockCount, Instant now) {
        jdbc.update("""
                        UPDATE users SET lock_count = :lockCount, last_locked_at = :now, failed_login_attempts = 0, first_failed_at = NULL
                        WHERE id = :id""",
                new MapSqlParameterSource("id", userId).addValue("lockCount", lockCount).addValue("now", Timestamp.from(now)));
    }
}
