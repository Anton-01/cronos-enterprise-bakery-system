package com.ninsky.cronos.account.shared.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * In-process Bucket4j buckets keyed by {@code action:userId} (the existing {@code RateLimitInterceptor}
 * is keyed by client IP, which is the wrong identity for per-user limits). Buckets idle for two
 * windows are evicted. Per-instance: behind several replicas the effective limit is N × budget —
 * see the Redis TODO in the module summary.
 */
@Component
public class Bucket4jAccountRateLimiter implements AccountRateLimiter {

    private final AccountRateLimitProperties properties;
    private final Cache<String, Bucket> buckets;

    public Bucket4jAccountRateLimiter(AccountRateLimitProperties properties) {
        this.properties = properties;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(properties.window().multipliedBy(2))
                .maximumSize(200_000)
                .build();
    }

    @Override
    public Decision tryConsume(UUID userId, RateLimitedAction action) {
        Bucket bucket = buckets.get(action.name() + ':' + userId, key -> newBucket(budgetFor(action)));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.allow();
        }
        return Decision.deny(TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
    }

    private int budgetFor(RateLimitedAction action) {
        return switch (action) {
            case AVATAR_UPLOAD -> properties.avatarUploadsPerWindow();
            case PROFILE_WRITE -> properties.profileWritesPerWindow();
            case FISCAL_WRITE -> properties.fiscalWritesPerWindow();
        };
    }

    private Bucket newBucket(int budget) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(budget).refillIntervally(budget, properties.window()).build())
                .build();
    }
}
