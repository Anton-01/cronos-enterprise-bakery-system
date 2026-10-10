package com.ninsky.cronos.mail.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Support trail of sent email ({@code mail_messages}): one row per {@code mail.requested} event, address hashed. */
@Repository
public class MailMessageCustomRepository {

    /** Delivery state of one email. */
    public enum Status { PENDING, SENT, RETRYING, FAILED }

    private final NamedParameterJdbcTemplate jdbc;

    public MailMessageCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates or updates the row of {@code eventId} with the outcome of attempt number {@code attempts}. */
    public void record(UUID eventId, String templateKey, String locale, String to, Status status, int attempts, String providerId,
                       String error, Instant now, String traceId) {
        jdbc.update("""
                        INSERT INTO mail_messages (id, event_id, template_key, locale, to_hash, status, provider_id, attempts,
                                                   last_error, created_at, sent_at, trace_id)
                        VALUES (:id, :eventId, :templateKey, :locale, :toHash, :status, :providerId, :attempts, :error, :now,
                                CASE WHEN :status = 'SENT' THEN CAST(:now AS timestamptz) END, :traceId)
                        ON CONFLICT (event_id) DO UPDATE SET status = EXCLUDED.status, provider_id = EXCLUDED.provider_id,
                            attempts = EXCLUDED.attempts, last_error = EXCLUDED.last_error, sent_at = EXCLUDED.sent_at""",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("eventId", eventId)
                        .addValue("templateKey", templateKey)
                        .addValue("locale", locale)
                        .addValue("toHash", hash(to))
                        .addValue("status", status.name())
                        .addValue("providerId", providerId)
                        .addValue("attempts", attempts)
                        .addValue("error", error == null || error.length() <= 1000 ? error : error.substring(0, 1000))
                        .addValue("now", Timestamp.from(now))
                        .addValue("traceId", traceId));
    }

    /** SHA-256 hex of the lower-cased address: lets support find "the mail to x" without storing x. */
    public static String hash(String address) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(address.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
