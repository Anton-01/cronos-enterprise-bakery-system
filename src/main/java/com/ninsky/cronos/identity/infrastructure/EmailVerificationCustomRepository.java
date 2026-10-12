package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.iam.token.TokenCodec;
import com.ninsky.cronos.iam.token.TokenPurpose;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Email verification state of accounts ({@code users.email_verified}) and lookups of spent links. */
@Repository
public class EmailVerificationCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EmailVerificationCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Marks the email verified; false when it already was. */
    public boolean markVerified(UUID userId) {
        return jdbc.update("""
                UPDATE users SET email_verified = TRUE, version = version + 1, updated_at = now()
                WHERE id = :id AND NOT email_verified""", Map.of("id", userId)) == 1;
    }

    /** Whether the account's email is verified (false for an unknown account). */
    public boolean isVerified(UUID userId) {
        return Boolean.TRUE.equals(jdbc.query("SELECT email_verified FROM users WHERE id = :id", Map.of("id", userId),
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE));
    }

    /** The holder of a link of {@code purpose}, valid or not (used to answer ALREADY_VERIFIED for a spent link). */
    public Optional<UUID> holderOfAnyLink(String rawToken, TokenPurpose purpose) {
        if (!TokenCodec.wellFormed(rawToken)) {
            return Optional.empty();
        }
        return jdbc.queryForList("SELECT user_id FROM user_tokens WHERE token_hash = :hash AND purpose = :purpose",
                new MapSqlParameterSource("hash", TokenCodec.hash(rawToken)).addValue("purpose", purpose.name()), UUID.class)
                .stream().findFirst();
    }
}
