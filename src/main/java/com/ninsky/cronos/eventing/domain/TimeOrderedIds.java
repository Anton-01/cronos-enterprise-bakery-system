package com.ninsky.cronos.eventing.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RFC 9562 UUIDv7 generator: 48-bit Unix milliseconds, then random bits. Ids sort by creation time
 * (PostgreSQL compares UUIDs byte-wise), which the outbox relay and the SSE {@code Last-Event-ID} rely on.
 */
public final class TimeOrderedIds {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SEQUENCE_BITS = 12;
    /** Last issued {@code millis << 12 | sequence}: ids of one millisecond count up, so they sort in issue order. */
    private static final AtomicLong LAST = new AtomicLong();

    private TimeOrderedIds() {
    }

    /**
     * A new UUIDv7 stamped with {@code clock}'s current millisecond, strictly greater than every id issued before by
     * this process (RFC 9562 §6.2, method 1: {@code rand_a} is a sequence that restarts each millisecond and carries
     * into the timestamp when it overflows or the clock steps back). Outbox dispatch order and stream replay rely on it.
     */
    public static UUID next(Clock clock) {
        long candidate = clock.millis() << SEQUENCE_BITS;
        long stamp = LAST.updateAndGet(previous -> candidate > previous ? candidate : previous + 1);
        long millis = stamp >>> SEQUENCE_BITS;
        long sequence = stamp & ((1L << SEQUENCE_BITS) - 1);
        long mostSignificant = (millis << 16) | 0x7000L | sequence;
        long leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }

    /** The smallest UUIDv7 of {@code millis}: a lower bound for range queries on time-ordered ids. */
    public static UUID lowerBound(long millis) {
        return new UUID((millis << 16) | 0x7000L, 0x8000_0000_0000_0000L);
    }
}
