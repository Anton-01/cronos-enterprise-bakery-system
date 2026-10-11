package com.ninsky.cronos.identity.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token buckets of the anonymous endpoints (§2.3.2), independent of the account lockout. In-memory per instance
 * (Caffeine), like the existing account limiter; a {@code bucket4j-redis} store fits behind the same methods when
 * several replicas run.
 */
@Component
public class AuthRateLimiter {

    /** One limited dimension; every bandwidth must have a token. */
    public enum Limit {
        SIGN_IN_IP(window(20, Duration.ofMinutes(1)), window(200, Duration.ofHours(1))),
        SIGN_IN_IDENTIFIER(window(10, Duration.ofMinutes(15))),
        REGISTER_IP(window(10, Duration.ofHours(1))),
        AVAILABILITY_IP(window(60, Duration.ofHours(1))),
        RECOVERY_IDENTIFIER(window(1, Duration.ofMinutes(1)), window(5, Duration.ofHours(1))),
        RECOVERY_IP(window(30, Duration.ofHours(1))),
        PUBLIC_QUOTE(window(60, Duration.ofHours(1))),
        PUSH_RENEW_IP(window(30, Duration.ofHours(1)));

        private final List<Bandwidth> bandwidths;

        Limit(Bandwidth... bandwidths) {
            this.bandwidths = List.of(bandwidths);
        }

        private static Bandwidth window(long capacity, Duration period) {
            return Bandwidth.builder().capacity(capacity).refillIntervally(capacity, period).build();
        }
    }

    private final Map<Limit, Cache<String, Bucket>> buckets = new EnumMap<>(Limit.class);
    private final boolean enabled;

    public AuthRateLimiter(AuthProperties properties) {
        this.enabled = properties.rateLimitsEnabled();
        for (Limit limit : Limit.values()) {
            buckets.put(limit, Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(2)).maximumSize(100_000).build());
        }
    }

    /**
     * Takes one token for {@code key}.
     *
     * @throws ApiException RATE_LIMITED with {@code Retry-After} and {@code details.retryAfterSeconds}
     */
    public void acquire(Limit limit, String key) {
        if (!enabled || key == null || key.isBlank()) {
            return;
        }
        Bucket bucket = buckets.get(limit).get(key, k -> {
            var builder = Bucket.builder();
            limit.bandwidths.forEach(builder::addLimit);
            return builder.build();
        });
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw ApiException.rateLimitedWithDetails(TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        }
    }

    /** True when the next {@link #acquire} would pass (no token is taken). */
    public boolean available(Limit limit, String key) {
        if (!enabled || key == null) {
            return true;
        }
        Bucket bucket = buckets.get(limit).getIfPresent(key);
        return bucket == null || bucket.getAvailableTokens() > 0;
    }
}
