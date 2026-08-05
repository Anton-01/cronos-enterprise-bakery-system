package com.ninsky.cronos.infrastructure.security.dpop;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed DPoP proof replay guard (RFC 9449 §11.1): a proof's {@code jti} must not be
 * accepted twice within its freshness window. Same style as Phase 4a's {@code TokenBlacklistService}.
 */
@Service
@RequiredArgsConstructor
public class DpopReplayGuard {

    private static final String JTI_PREFIX = "dpop:jti:";

    private final StringRedisTemplate redisTemplate;

    /** Atomically records {@code jti} as seen; returns false if it was already recorded (replay). */
    public boolean markAndCheckNotReplayed(String jti, Duration ttl) {
        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(JTI_PREFIX + jti, "1", ttl);
        return Boolean.TRUE.equals(firstTime);
    }
}
