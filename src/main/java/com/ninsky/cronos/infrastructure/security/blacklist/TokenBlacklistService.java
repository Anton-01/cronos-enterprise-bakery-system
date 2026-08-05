package com.ninsky.cronos.infrastructure.security.blacklist;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Redis-backed access-token revocation check. Access tokens are stateless JWTs with no server-side
 * record, so revoking a session/user doesn't invalidate tokens already issued — this fills that gap
 * with two write shapes, checked on every authenticated request by {@code JwtAuthenticationFilter}:
 * <ul>
 *   <li>Session-level: one key per terminated session, covers every token issued under it.</li>
 *   <li>User-level: a "revoked no earlier than" cutoff timestamp, covers bulk revocation
 *       (force-logout-all, password change) without enumerating sessions/tokens.</li>
 * </ul>
 * No domain port here — this is cross-cutting infra (like the Caffeine cache in {@code CacheConfig}),
 * not an aggregate repository.
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String SESSION_PREFIX = "blacklist:session:";
    private static final String USER_PREFIX = "blacklist:user:";

    private final StringRedisTemplate redisTemplate;

    public void blacklistSession(UUID sessionId, Duration ttl) {
        if (sessionId == null) {
            return;
        }
        redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, "1", ttl);
    }

    public boolean isSessionBlacklisted(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_PREFIX + sessionId));
    }

    public void blacklistUser(UUID userId, Duration ttl) {
        if (userId == null) {
            return;
        }
        redisTemplate.opsForValue().set(USER_PREFIX + userId, String.valueOf(System.currentTimeMillis()), ttl);
    }

    /** True if {@code userId} has a bulk revocation on record issued at or after {@code issuedAt}. */
    public boolean isTokenRevokedForUser(UUID userId, Date issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        String value = redisTemplate.opsForValue().get(USER_PREFIX + userId);
        if (value == null) {
            return false;
        }
        long revokedAtMillis = Long.parseLong(value);
        return issuedAt.getTime() < revokedAtMillis;
    }
}
