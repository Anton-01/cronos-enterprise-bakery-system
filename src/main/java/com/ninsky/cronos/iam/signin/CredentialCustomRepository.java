package com.ninsky.cronos.iam.signin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /** Clears the failed-attempt counter and stamps the login time. */
    public void recordSuccessfulLogin(UUID userId, LocalDateTime now) {
        jdbc.update("UPDATE users SET failed_login_attempts = 0, last_login_at = :now WHERE id = :id",
                new MapSqlParameterSource("id", userId).addValue("now", now));
    }
}
