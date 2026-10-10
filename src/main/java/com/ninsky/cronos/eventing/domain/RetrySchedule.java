package com.ninsky.cronos.eventing.domain;

import java.time.Duration;
import java.util.List;

/**
 * Back-off schedules of the outbox relay. The n-th failed attempt waits {@code delays[n-1]} (the last
 * delay repeats); after {@code maxAttempts} failures the message becomes {@code DEAD}.
 */
public enum RetrySchedule {

    /** Integration events: 1 s, 5 s, 30 s, 2 min, 10 min, 1 h; dead after 10 attempts. */
    DEFAULT(List.of(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2),
            Duration.ofMinutes(10), Duration.ofHours(1)), 10),

    /** Outgoing email: 1 min, 5 min, 30 min, 2 h, 6 h; dead (FAILED for support) after the sixth attempt. */
    MAIL(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2),
            Duration.ofHours(6)), 6),

    /** Web Push: providers ask for quick retries; a subscription failing five times in a row is revoked anyway. */
    PUSH(List.of(Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30)), 5);

    private final List<Duration> delays;
    private final int maxAttempts;

    RetrySchedule(List<Duration> delays, int maxAttempts) {
        this.delays = List.copyOf(delays);
        this.maxAttempts = maxAttempts;
    }

    /**
     * Delay before the next attempt after {@code failedAttempts} failures.
     *
     * @throws IllegalArgumentException when {@code failedAttempts} is not positive
     */
    public Duration delayAfter(int failedAttempts) {
        if (failedAttempts < 1) {
            throw new IllegalArgumentException("failedAttempts must be positive");
        }
        return delays.get(Math.min(failedAttempts, delays.size()) - 1);
    }

    /** True when a message that failed {@code failedAttempts} times must not be retried again. */
    public boolean exhausted(int failedAttempts) {
        return failedAttempts >= maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
