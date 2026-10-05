package com.ninsky.cronos.iam.shared;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** In-memory token bucket per key (actor, target user…) for the limits of spec §1.6. */
public final class KeyedRateLimiter {

    private final long capacity;
    private final Duration window;
    private final Cache<Object, Bucket> buckets;

    public KeyedRateLimiter(long capacity, Duration window) {
        this.capacity = capacity;
        this.window = window;
        this.buckets = Caffeine.newBuilder().expireAfterAccess(window.multipliedBy(2)).maximumSize(50_000).build();
    }

    /** @throws ApiException RATE_LIMITED with the seconds until a token is available */
    public void acquire(Object key) {
        ConsumptionProbe probe = buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw ApiException.rateLimited(TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, window).build())
                .build();
    }
}
