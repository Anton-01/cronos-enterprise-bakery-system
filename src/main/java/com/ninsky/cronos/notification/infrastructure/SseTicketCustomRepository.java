package com.ninsky.cronos.notification.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** SQL of {@code sse_tickets}: hashed, single-use, short-lived. */
@Repository
public class SseTicketCustomRepository {

    /** Who a consumed ticket authenticates. */
    public record Ticket(UUID userId, UUID sessionId) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public SseTicketCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String ticketHash, UUID userId, UUID sessionId, Instant expiresAt) {
        jdbc.update("INSERT INTO sse_tickets (ticket_hash, user_id, session_id, expires_at) VALUES (:hash, :user, :session, :expires)",
                new MapSqlParameterSource("hash", ticketHash).addValue("user", userId).addValue("session", sessionId)
                        .addValue("expires", Timestamp.from(expiresAt)));
    }

    /** Deletes the ticket and returns it when it was still valid (delete-on-read: a ticket works once). */
    public Optional<Ticket> consume(String ticketHash, Instant now) {
        return jdbc.query("""
                                DELETE FROM sse_tickets WHERE ticket_hash = :hash
                                RETURNING user_id, session_id, expires_at > :now AS valid""",
                        new MapSqlParameterSource("hash", ticketHash).addValue("now", Timestamp.from(now)),
                        (rs, n) -> rs.getBoolean("valid") ? new Ticket(rs.getObject("user_id", UUID.class), rs.getObject("session_id", UUID.class))
                                : null)
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }
}
