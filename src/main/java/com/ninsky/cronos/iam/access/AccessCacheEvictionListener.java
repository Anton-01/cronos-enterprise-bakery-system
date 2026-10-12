package com.ninsky.cronos.iam.access;

import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import com.ninsky.cronos.infrastructure.security.session.SessionStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Optional;

/**
 * Drops version and auth projections of affected users once the change is committed, and publishes the new access
 * versions to the Redis session state so access tokens minted before the change are refused on their next request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessCacheEvictionListener {

    private final CacheManager cacheManager;
    private final SessionStateStore sessionState;
    private final JwtConfig jwtConfig;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AccessChanged event) {
        Optional.ofNullable(cacheManager.getCache(AccessCaches.ACCESS_VERSION))
                .ifPresent(cache -> event.userIds().forEach(cache::evict));
        // Auth projections are also keyed by login id, which is unknown here: clear them all.
        Optional.ofNullable(cacheManager.getCache(AccessCaches.USER_AUTH)).ifPresent(Cache::clear);
        if (!event.versions().isEmpty()) {
            try {
                sessionState.publishAccessVersions(event.versions(), Duration.ofMillis(jwtConfig.getAccessTokenExpiration()));
            } catch (RuntimeException e) {
                // The change is committed; without the Redis key older tokens live until they expire (≤ 15 min).
                log.error("Could not publish access versions of {}: {}", event.versions().keySet(), e.getMessage());
            }
        }
    }
}
