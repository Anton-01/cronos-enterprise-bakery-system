package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores a new password (existing encoder) and its history entry, clears the forced-change flag and
 * the failed-attempt counter, bumps {@code version}. JDBC like every other IAM user write.
 */
@Component
@RequiredArgsConstructor
public class CredentialWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuthProjectionCache authCache;
    private final Clock clock;

    @Transactional
    public void setPassword(UUID userId, String rawPassword, boolean emailVerified) {
        String hash = encoder.encode(rawPassword);
        LocalDateTime now = TenantTime.nowLocal(clock);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", userId)
                .addValue("hash", hash)
                .addValue("now", now)
                .addValue("verified", emailVerified);
        jdbc.update("""
                UPDATE users SET password = :hash, password_changed_at = :now, password_needs_change = FALSE,
                       credentials_non_expired = TRUE, failed_login_attempts = 0,
                       email_verified = email_verified OR :verified, version = version + 1, updated_at = :now
                WHERE id = :id""", params);
        jdbc.update("INSERT INTO password_history (id, user_id, password_hash, changed_at) VALUES (:historyId, :id, :hash, :now)",
                params.addValue("historyId", UUID.randomUUID()));
        authCache.evictAfterCommit();
    }
}
