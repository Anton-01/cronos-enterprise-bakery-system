package com.ninsky.cronos.infrastructure.security.session;

import com.ninsky.cronos.infrastructure.security.blacklist.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-session state the authentication filter needs on every request, kept in Redis so that a valid JWT is enough to
 * authenticate without touching PostgreSQL (performance-latency P0-4). One {@code MGET} answers everything:
 * <ul>
 *   <li>{@code session:{sid}}: {@code ACTIVE | LOCKED | REVOKED} (absent means active), written when the screen of a
 *       session is locked or unlocked and when the session ends;</li>
 *   <li>{@code blacklist:session:{sid}} and {@code blacklist:user:{uid}}: the revocation keys of
 *       {@link TokenBlacklistService};</li>
 *   <li>{@code access:version:{uid}}: the user's current access version after a committed access change, so tokens
 *       minted before it are refused and the client refreshes.</li>
 * </ul>
 * PostgreSQL stays the source of truth ({@code user_sessions.locked_at}, {@code users.access_version}); the refresh
 * endpoint re-checks it, so a lost Redis key at worst lets an access token live until it expires (15 minutes).
 */
@Component
public class SessionStateStore {

    static final String SESSION_PREFIX = "session:";
    static final String ACCESS_VERSION_PREFIX = "access:version:";

    /** Screen-lock and revocation state of one session. */
    public enum Status {
        ACTIVE, LOCKED, REVOKED
    }

    /**
     * What Redis knows about the session and user of one access token.
     *
     * @param status                 state of the session (ACTIVE when unknown)
     * @param sessionBlacklisted     the session's access tokens were revoked
     * @param userRevokedAtMillis    bulk revocation cutoff of the user, null when none
     * @param currentAccessVersion   access version after the user's last access change, null when unknown
     */
    public record Check(Status status, boolean sessionBlacklisted, Long userRevokedAtMillis, Long currentAccessVersion) {

        /** True when a token issued at {@code issuedAt} with access version {@code tokenAccessVersion} must be refused. */
        public boolean rejects(Date issuedAt, Long tokenAccessVersion) {
            if (status == Status.REVOKED || sessionBlacklisted) {
                return true;
            }
            if (userRevokedAtMillis != null && (issuedAt == null || issuedAt.getTime() < userRevokedAtMillis)) {
                return true;
            }
            if (currentAccessVersion == null) {
                return false;
            }
            return tokenAccessVersion == null ? currentAccessVersion != 0 : tokenAccessVersion < currentAccessVersion;
        }
    }

    private final StringRedisTemplate redis;
    private final Duration sessionTtl;

    public SessionStateStore(StringRedisTemplate redis,
                             @Value("${app.security.session-state-ttl:P30D}") Duration sessionTtl) {
        this.redis = redis;
        this.sessionTtl = sessionTtl;
    }

    /** One round trip: session status, revocation keys and current access version. */
    public Check check(UUID sessionId, UUID userId) {
        String sid = sessionId == null ? "-" : sessionId.toString();
        String uid = userId == null ? "-" : userId.toString();
        List<String> values = redis.opsForValue().multiGet(Arrays.asList(
                SESSION_PREFIX + sid,
                TokenBlacklistService.SESSION_PREFIX + sid,
                TokenBlacklistService.USER_PREFIX + uid,
                ACCESS_VERSION_PREFIX + uid));
        if (values == null) {
            return new Check(Status.ACTIVE, false, null, null);
        }
        return new Check(status(values.get(0)), sessionId != null && values.get(1) != null, number(values.get(2)), number(values.get(3)));
    }

    /** The screen of {@code sessionId} is locked. */
    public void markLocked(UUID sessionId) {
        set(sessionId, Status.LOCKED);
    }

    /** The screen of {@code sessionId} is unlocked. */
    public void markActive(UUID sessionId) {
        set(sessionId, Status.ACTIVE);
    }

    /** {@code sessionId} ended (sign-out, expiry, concurrency limit, administrative revocation). */
    public void markRevoked(UUID sessionId) {
        set(sessionId, Status.REVOKED);
    }

    /** Records committed access versions; tokens below them are refused for {@code ttl} (the access-token lifetime). */
    public void publishAccessVersions(Map<UUID, Long> versions, Duration ttl) {
        versions.forEach((userId, version) -> redis.opsForValue().set(ACCESS_VERSION_PREFIX + userId, String.valueOf(version), ttl));
    }

    private void set(UUID sessionId, Status status) {
        if (sessionId != null) {
            redis.opsForValue().set(SESSION_PREFIX + sessionId, status.name(), sessionTtl);
        }
    }

    private static Status status(String value) {
        if (value == null) {
            return Status.ACTIVE;
        }
        try {
            return Status.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return Status.ACTIVE;
        }
    }

    private static Long number(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
