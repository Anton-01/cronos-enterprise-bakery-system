package com.ninsky.cronos.iam.token;

import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link UserTokens} over {@code user_tokens}; consumption is a single conditional UPDATE, so a token works once. */
@Component
@RequiredArgsConstructor
public class JdbcUserTokens implements UserTokens {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedToken issue(UUID userId, TokenPurpose purpose, Duration ttl) {
        invalidate(userId, purpose);
        Instant now = TenantTime.now(clock);
        Instant expiresAt = now.plus(ttl);
        String raw = TokenCodec.generate();
        jdbc.update("""
                INSERT INTO user_tokens (id, user_id, purpose, token_hash, expires_at, created_at)
                VALUES (:id, :userId, :purpose, :hash, :expiresAt, :now)""",
                params(userId, purpose, now)
                        .addValue("id", UUID.randomUUID())
                        .addValue("hash", TokenCodec.hash(raw))
                        .addValue("expiresAt", Timestamp.from(expiresAt)));
        return new IssuedToken(raw, expiresAt);
    }

    @Override
    @Transactional
    public Optional<UUID> consume(String rawToken, TokenPurpose purpose) {
        if (!TokenCodec.wellFormed(rawToken)) {
            return Optional.empty();
        }
        return jdbc.queryForList("""
                        UPDATE user_tokens SET used_at = :now
                        WHERE token_hash = :hash AND purpose = :purpose AND used_at IS NULL AND expires_at > :now
                        RETURNING user_id""",
                params(null, purpose, TenantTime.now(clock)).addValue("hash", TokenCodec.hash(rawToken)), UUID.class)
                .stream().findFirst();
    }

    @Override
    public Optional<Instant> pendingExpiry(UUID userId, TokenPurpose purpose) {
        return jdbc.queryForList("""
                        SELECT max(expires_at) FROM user_tokens
                        WHERE user_id = :userId AND purpose = :purpose AND used_at IS NULL AND expires_at > :now""",
                params(userId, purpose, TenantTime.now(clock)), Timestamp.class)
                .stream().filter(Objects::nonNull).findFirst().map(Timestamp::toInstant);
    }

    @Override
    public void invalidate(UUID userId, TokenPurpose purpose) {
        jdbc.update("UPDATE user_tokens SET used_at = :now WHERE user_id = :userId AND purpose = :purpose AND used_at IS NULL",
                params(userId, purpose, TenantTime.now(clock)));
    }

    private static MapSqlParameterSource params(UUID userId, TokenPurpose purpose, Instant now) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("purpose", purpose.name())
                .addValue("now", Timestamp.from(now));
    }
}
