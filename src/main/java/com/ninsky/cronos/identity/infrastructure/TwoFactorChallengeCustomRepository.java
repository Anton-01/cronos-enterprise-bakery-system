package com.ninsky.cronos.identity.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Second sign-in step ({@code two_factor_challenges}): short-lived, at most five codes, single use. */
@Repository
public class TwoFactorChallengeCustomRepository {

    /** A challenge as stored. */
    public record Challenge(UUID id, UUID userId, boolean rememberMe, int attempts, Instant expiresAt, Instant consumedAt) {

        /** Usable at {@code now}. */
        public boolean open(Instant now) {
            return consumedAt == null && expiresAt.isAfter(now);
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public TwoFactorChallengeCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates a challenge for {@code userId}; returns its id (the {@code challengeId} answered to the client). */
    public UUID create(UUID userId, boolean rememberMe, String ip, Instant now, Duration ttl) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO two_factor_challenges (id, user_id, remember_me, ip_address, attempts, created_at, expires_at)
                        VALUES (:id, :userId, :remember, :ip, 0, :now, :expires)""",
                new MapSqlParameterSource()
                        .addValue("id", id).addValue("userId", userId).addValue("remember", rememberMe)
                        .addValue("ip", ip == null || ip.length() <= 45 ? ip : ip.substring(0, 45))
                        .addValue("now", Timestamp.from(now)).addValue("expires", Timestamp.from(now.plus(ttl))));
        return id;
    }

    /** The challenge, row-locked for the rest of the transaction. */
    public Optional<Challenge> lock(UUID id) {
        return jdbc.query("SELECT * FROM two_factor_challenges WHERE id = :id FOR UPDATE", Map.of("id", id),
                (rs, row) -> new Challenge(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getBoolean("remember_me"), rs.getInt("attempts"), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()))
                .stream().findFirst();
    }

    /** Counts a wrong code; consumes the challenge when {@code maxAttempts} is reached. Returns the attempts used. */
    public int fail(UUID id, int maxAttempts, Instant now) {
        Integer attempts = jdbc.queryForObject("""
                        UPDATE two_factor_challenges SET attempts = attempts + 1,
                               consumed_at = CASE WHEN attempts + 1 >= :max THEN :now ELSE consumed_at END
                        WHERE id = :id RETURNING attempts""",
                new MapSqlParameterSource("id", id).addValue("max", maxAttempts).addValue("now", Timestamp.from(now)), Integer.class);
        return attempts == null ? maxAttempts : attempts;
    }

    /** Marks the challenge used. */
    public void consume(UUID id, Instant now) {
        jdbc.update("UPDATE two_factor_challenges SET consumed_at = :now WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(now)));
    }

    /** Deletes challenges expired before {@code cutoff}. */
    public int purgeExpired(Instant cutoff) {
        return jdbc.update("DELETE FROM two_factor_challenges WHERE expires_at < :cutoff", Map.of("cutoff", Timestamp.from(cutoff)));
    }
}
