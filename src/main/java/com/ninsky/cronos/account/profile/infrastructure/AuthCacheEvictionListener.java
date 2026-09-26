package com.ninsky.cronos.account.profile.infrastructure;

import com.ninsky.cronos.account.profile.domain.UsernameChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code JdbcUserAuthAdapter} caches auth projections under the login id and under {@code id:{userId}}.
 * After a committed rename, drop both so the principal (and {@code Authentication#getName()}, used
 * e.g. as the JPA auditor) reflects the new username immediately and the old name stops resolving.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCacheEvictionListener {

    static final String USER_AUTH_CACHE = "userAuth";

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UsernameChanged event) {
        Cache cache = cacheManager.getCache(USER_AUTH_CACHE);
        if (cache == null) {
            return;
        }
        cache.evict(event.previousUsername());
        cache.evict(event.newUsername());
        cache.evict("id:" + event.userId());
        log.debug("Evicted auth cache entries for renamed user {}", event.userId());
    }
}
