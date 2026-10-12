package com.ninsky.cronos.notification.infrastructure;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Stream tickets in Redis (performance-latency P1-7): {@code SET stream-ticket:{sha256} {userId}:{sessionId} NX EX ttl}
 * when issued and {@code GETDEL} when the stream opens, so a ticket works once and the whole exchange never touches
 * PostgreSQL. Only the SHA-256 of the ticket is stored. (The {@code sse_tickets} table is no longer written; the token
 * clean-up job still empties it.)
 */
@Repository
public class SseTicketCustomRepository {

    static final String PREFIX = "stream-ticket:";
    private static final String NO_SESSION = "-";

    /** Who a consumed ticket authenticates. */
    public record Ticket(UUID userId, UUID sessionId) {
    }

    private final StringRedisTemplate redis;

    public SseTicketCustomRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Stores a new ticket for {@code ttl}; false only on a (practically impossible) hash collision. */
    public boolean insert(String ticketHash, UUID userId, UUID sessionId, Duration ttl) {
        String value = userId + ":" + (sessionId == null ? NO_SESSION : sessionId.toString());
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + ticketHash, value, ttl));
    }

    /** Deletes the ticket and returns its owner when it still existed (expired tickets are gone already). */
    public Optional<Ticket> consume(String ticketHash) {
        String value = redis.opsForValue().getAndDelete(PREFIX + ticketHash);
        if (value == null) {
            return Optional.empty();
        }
        int separator = value.indexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        String session = value.substring(separator + 1);
        try {
            return Optional.of(new Ticket(UUID.fromString(value.substring(0, separator)),
                    NO_SESSION.equals(session) ? null : UUID.fromString(session)));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
