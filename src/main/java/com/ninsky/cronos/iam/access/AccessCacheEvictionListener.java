package com.ninsky.cronos.iam.access;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/** Drops version and auth projections of affected users once the change is committed. */
@Component
@RequiredArgsConstructor
public class AccessCacheEvictionListener {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AccessChanged event) {
        Optional.ofNullable(cacheManager.getCache(AccessCaches.ACCESS_VERSION))
                .ifPresent(cache -> event.userIds().forEach(cache::evict));
        // Auth projections are also keyed by login id, which is unknown here: clear them all.
        Optional.ofNullable(cacheManager.getCache(AccessCaches.USER_AUTH)).ifPresent(Cache::clear);
    }
}
