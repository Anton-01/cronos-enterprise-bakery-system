package com.ninsky.cronos.eventing.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * RFC 9562 UUIDv7 generator: 48-bit Unix milliseconds, then random bits. Ids sort by creation time
 * (PostgreSQL compares UUIDs byte-wise), which the outbox relay and the SSE {@code Last-Event-ID} rely on.
 */
public final class TimeOrderedIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TimeOrderedIds() {
    }

    /** A new UUIDv7 stamped with {@code clock}'s current millisecond. */
    public static UUID next(Clock clock) {
        long millis = clock.millis();
        long randA = RANDOM.nextInt(1 << 12);
        long mostSignificant = (millis << 16) | 0x7000L | randA;
        long leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }

    /** The smallest UUIDv7 of {@code millis}: a lower bound for range queries on time-ordered ids. */
    public static UUID lowerBound(long millis) {
        return new UUID((millis << 16) | 0x7000L, 0x8000_0000_0000_0000L);
    }
}
