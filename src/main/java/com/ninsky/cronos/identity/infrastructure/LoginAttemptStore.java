package com.ninsky.cronos.identity.infrastructure;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Failed sign-in counters and the lockout window in Redis (performance-latency P0-5): a wrong password costs Redis
 * commands, not a PostgreSQL write on the request path. The durable account state ({@code users.status = LOCKED},
 * {@code failed_login_attempts}, {@code lock_count}) follows through the {@code AccountLocked}/{@code SignInFailed}
 * consumers.
 * <ul>
 *   <li>{@code signin:failures:{uid}}: failures in the current window ({@code INCR}, expiry set on the first one);</li>
 *   <li>{@code signin:lock:{uid}}: present while the account is locked, value = lock end (epoch millis);</li>
 *   <li>{@code signin:lock-count:{uid}}: locks within the lock memory, for the progressive schedule.</li>
 * </ul>
 */
@Component
public class LoginAttemptStore {

    static final String FAILURES = "signin:failures:";
    static final String LOCK = "signin:lock:";
    static final String LOCK_COUNT = "signin:lock-count:";

    /** INCR and, on the first increment, PEXPIRE, atomically: the window starts at the first failure. */
    private static final RedisScript<Long> INCREMENT_IN_WINDOW = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count""", Long.class);

    private final StringRedisTemplate redis;

    public LoginAttemptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts one failure inside a window of {@code window}; returns the failures counted so far. */
    public int registerFailure(UUID userId, Duration window) {
        Long count = redis.execute(INCREMENT_IN_WINDOW, List.of(FAILURES + userId), String.valueOf(window.toMillis()));
        return count == null ? 1 : count.intValue();
    }

    /** Clears the failure window after a successful sign-in. */
    public void clearFailures(UUID userId) {
        redis.delete(FAILURES + userId);
    }

    /** Counts one more lock within {@code lockMemory}; returns the lock number (1 for the first lock). */
    public int nextLockCount(UUID userId, Duration lockMemory) {
        Long lockCount = redis.execute(INCREMENT_IN_WINDOW, List.of(LOCK_COUNT + userId), String.valueOf(lockMemory.toMillis()));
        return lockCount == null ? 1 : lockCount.intValue();
    }

    /** Locks the account until {@code until} and clears the failure window. */
    public void lock(UUID userId, Instant now, Instant until) {
        Duration duration = Duration.between(now, until);
        if (!duration.isNegative() && !duration.isZero()) {
            redis.opsForValue().set(LOCK + userId, String.valueOf(until.toEpochMilli()), duration);
        }
        redis.delete(FAILURES + userId);
    }

    /** End of the current lock, if the account is locked. */
    public Optional<Instant> lockedUntil(UUID userId, Instant now) {
        String value = redis.opsForValue().get(LOCK + userId);
        if (value == null) {
            return Optional.empty();
        }
        try {
            Instant until = Instant.ofEpochMilli(Long.parseLong(value));
            return until.isAfter(now) ? Optional.of(until) : Optional.empty();
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }

    /** Lifts a lock (password reset, administrator, expiry handled by the database state). */
    public void unlock(UUID userId) {
        redis.delete(List.of(LOCK + userId, FAILURES + userId));
    }
}
