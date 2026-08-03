package com.ninsky.cronos.infrastructure.util.auth;


import com.ninsky.cronos.infrastructure.config.security.RateLimit;
import com.ninsky.cronos.infrastructure.config.security.SecurityProperties;
import com.ninsky.cronos.infrastructure.exception.RateLimitExceededException;
import io.github.bucket4j.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor{

    private final CacheManager cacheManager;
    private final SecurityProperties securityProperties;
    private final RequestContextUtil requestContextUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return true;
        }

        String rateLimitKey = rateLimit.key();
        String clientIp = requestContextUtil.getClientIp();
        String cacheKey = String.format("%s:%s", rateLimitKey, clientIp);

        Bucket bucket = resolveBucket(cacheKey, rateLimitKey);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));

        log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, rateLimitKey);

        throw new RateLimitExceededException(
                String.format("Rate limit exceeded. Try again in %d seconds", waitForRefill)
        );
    }

    private Bucket resolveBucket(String cacheKey, String rateLimitKey) {
        Cache cache = cacheManager.getCache("rate-limits");
        if (cache == null) {
            throw new IllegalStateException("Rate limit cache not configured");
        }

        Bucket bucket = cache.get(cacheKey, Bucket.class);

        if (bucket == null) {
            bucket = createNewBucket(rateLimitKey);
            cache.put(cacheKey, bucket);
        }

        return bucket;
    }

    private Bucket createNewBucket(String rateLimitKey) {
        var rateLimitConfig = getRateLimitConfig(rateLimitKey);

        Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimitConfig.capacity)
                .refillIntervally(rateLimitConfig.refillTokens,
                        Duration.ofSeconds(rateLimitConfig.refillDurationSeconds))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private RateLimitConfig getRateLimitConfig(String key) {
        var rateLimit = securityProperties.getRateLimit();

        return switch (key) {
            case "login" -> new RateLimitConfig(
                    rateLimit.getLogin().getCapacity(),
                    rateLimit.getLogin().getRefillTokens(),
                    rateLimit.getLogin().getRefillDurationSeconds()
            );
            case "register" -> new RateLimitConfig(
                    rateLimit.getRegister().getCapacity(),
                    rateLimit.getRegister().getRefillTokens(),
                    rateLimit.getRegister().getRefillDurationSeconds()
            );
            case "refresh" -> new RateLimitConfig(
                    rateLimit.getRefresh().getCapacity(),
                    rateLimit.getRefresh().getRefillTokens(),
                    rateLimit.getRefresh().getRefillDurationSeconds()
            );
            default -> new RateLimitConfig(10, 10, 60);
        };
    }

    private record RateLimitConfig(long capacity, long refillTokens, long refillDurationSeconds) {}
}
