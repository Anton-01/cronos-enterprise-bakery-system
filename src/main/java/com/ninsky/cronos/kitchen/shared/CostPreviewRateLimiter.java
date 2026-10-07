package com.ninsky.cronos.kitchen.shared;

import com.ninsky.cronos.iam.shared.KeyedRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** 120 cost previews per minute per user (§5.6). */
@Component
public class CostPreviewRateLimiter {

    private final KeyedRateLimiter limiter;

    public CostPreviewRateLimiter(KitchenProperties properties) {
        this.limiter = new KeyedRateLimiter(properties.costPreviewsPerMinute(), Duration.ofMinutes(1));
    }

    /** @throws com.ninsky.cronos.infrastructure.exception.ApiException RATE_LIMITED */
    public void acquire(UUID userId) {
        limiter.acquire(userId);
    }
}
