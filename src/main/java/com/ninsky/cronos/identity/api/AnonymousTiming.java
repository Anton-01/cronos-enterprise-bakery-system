package com.ninsky.cronos.identity.api;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pads anonymous answers that must not reveal whether an account exists (forgot password, resend verification) to a
 * constant minimum duration plus jitter, so the work done for an existing account cannot be timed.
 */
final class AnonymousTiming {

    private static final long JITTER_MILLIS = 100;

    private AnonymousTiming() {
    }

    /** Sleeps until {@code minimum} (+ up to 100 ms) has passed since {@code startNanos}. */
    static void padSince(long startNanos, Duration minimum) {
        long target = minimum.toNanos() + ThreadLocalRandom.current().nextLong(JITTER_MILLIS * 1_000_000);
        long remaining = target - (System.nanoTime() - startNanos);
        if (remaining > 0) {
            try {
                Thread.sleep(Duration.ofNanos(remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
