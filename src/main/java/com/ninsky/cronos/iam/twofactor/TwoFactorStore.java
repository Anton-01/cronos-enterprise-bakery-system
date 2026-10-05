package com.ninsky.cronos.iam.twofactor;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JDBC access to enrolments, enrolled secrets and recovery-code hashes (contract §8.2 tables). */
@Repository
public class TwoFactorStore {

    public record Enrollment(UUID id, UUID userId, byte[] secretEnc, int failedTries, Instant expiresAt, Instant consumedAt) {
    }

    public record Enrolled(byte[] secretEnc, Long lastUsedStep, Instant enrolledAt) {
    }

    public record RecoveryCode(long id, String hash) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public TwoFactorStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Starting a new enrolment invalidates the user's previous pending ones. */
    public void startEnrollment(UUID id, UUID userId, byte[] secretEnc, Instant now, Instant expiresAt) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id).addValue("userId", userId)
                .addValue("secret", secretEnc).addValue("now", ts(now)).addValue("expiresAt", ts(expiresAt));
        jdbc.update("UPDATE two_factor_enrollments SET consumed_at = :now WHERE user_id = :userId AND consumed_at IS NULL", params);
        jdbc.update("""
                INSERT INTO two_factor_enrollments (id, user_id, secret_enc, expires_at, created_at)
                VALUES (:id, :userId, :secret, :expiresAt, :now)""", params);
    }

    /** Locks the row so concurrent confirmations serialise. */
    public Optional<Enrollment> lockEnrollment(UUID id, UUID userId) {
        return jdbc.query("""
                        SELECT id, user_id, secret_enc, failed_tries, expires_at, consumed_at
                        FROM two_factor_enrollments WHERE id = :id AND user_id = :userId FOR UPDATE""",
                new MapSqlParameterSource("id", id).addValue("userId", userId),
                (rs, i) -> new Enrollment(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getBytes("secret_enc"), rs.getInt("failed_tries"), instant(rs.getTimestamp("expires_at")),
                        instant(rs.getTimestamp("consumed_at")))).stream().findFirst();
    }

    /** @return failures so far */
    public int registerFailure(UUID id) {
        return jdbc.queryForObject("UPDATE two_factor_enrollments SET failed_tries = failed_tries + 1 WHERE id = :id RETURNING failed_tries",
                Map.of("id", id), Integer.class);
    }

    public void consumeEnrollment(UUID id, Instant now) {
        jdbc.update("UPDATE two_factor_enrollments SET consumed_at = :now WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("now", ts(now)));
    }

    public Optional<Enrolled> enrolled(UUID userId) {
        return jdbc.query("SELECT secret_enc, last_used_step, enrolled_at FROM user_two_factor WHERE user_id = :id",
                Map.of("id", userId), (rs, i) -> new Enrolled(rs.getBytes("secret_enc"),
                        rs.getObject("last_used_step", Long.class), instant(rs.getTimestamp("enrolled_at")))).stream().findFirst();
    }

    public void enrol(UUID userId, byte[] secretEnc, long usedStep, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", userId).addValue("secret", secretEnc)
                .addValue("step", usedStep).addValue("now", ts(now));
        jdbc.update("""
                INSERT INTO user_two_factor (user_id, secret_enc, last_used_step, enrolled_at)
                VALUES (:id, :secret, :step, :now)
                ON CONFLICT (user_id) DO UPDATE SET secret_enc = :secret, last_used_step = :step, enrolled_at = :now""", params);
        jdbc.update("UPDATE users SET two_factor_enabled = TRUE WHERE id = :id", params);
    }

    /** Atomically claims a time-step; false when it (or a later one) was already used. */
    public boolean claimStep(UUID userId, long step) {
        return jdbc.update("""
                UPDATE user_two_factor SET last_used_step = :step
                WHERE user_id = :id AND (last_used_step IS NULL OR last_used_step < :step)""",
                new MapSqlParameterSource("id", userId).addValue("step", step)) == 1;
    }

    /** Secret, recovery codes and pending enrolments go; the flag is cleared. */
    public void remove(UUID userId) {
        Map<String, UUID> params = Map.of("id", userId);
        jdbc.update("DELETE FROM user_two_factor WHERE user_id = :id", params);
        jdbc.update("DELETE FROM user_recovery_codes WHERE user_id = :id", params);
        jdbc.update("DELETE FROM two_factor_enrollments WHERE user_id = :id", params);
        jdbc.update("UPDATE users SET two_factor_enabled = FALSE WHERE id = :id", params);
    }

    public void replaceRecoveryCodes(UUID userId, Collection<String> hashes) {
        jdbc.update("DELETE FROM user_recovery_codes WHERE user_id = :id", Map.of("id", userId));
        jdbc.batchUpdate("INSERT INTO user_recovery_codes (user_id, code_hash) VALUES (:id, :hash)",
                hashes.stream().map(hash -> new MapSqlParameterSource("id", userId).addValue("hash", hash))
                        .toArray(SqlParameterSource[]::new));
    }

    public List<RecoveryCode> unusedRecoveryCodes(UUID userId) {
        return jdbc.query("SELECT id, code_hash FROM user_recovery_codes WHERE user_id = :id AND used_at IS NULL ORDER BY id",
                Map.of("id", userId), (rs, i) -> new RecoveryCode(rs.getLong("id"), rs.getString("code_hash")));
    }

    public int remainingRecoveryCodes(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM user_recovery_codes WHERE user_id = :id AND used_at IS NULL",
                Map.of("id", userId), Integer.class);
    }

    /** False when a concurrent request already used it. */
    public boolean useRecoveryCode(long id, Instant now) {
        return jdbc.update("UPDATE user_recovery_codes SET used_at = :now WHERE id = :id AND used_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("now", ts(now))) == 1;
    }

    public Optional<String> passwordHash(UUID userId) {
        return jdbc.queryForList("SELECT password FROM users WHERE id = :id", Map.of("id", userId), String.class)
                .stream().filter(Objects::nonNull).findFirst();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
