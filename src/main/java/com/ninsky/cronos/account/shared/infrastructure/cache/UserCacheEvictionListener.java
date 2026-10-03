package com.ninsky.cronos.account.shared.infrastructure.cache;

import com.ninsky.cronos.account.shared.infrastructure.audit.AuditRecorded;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code UserService#getUserById} caches {@code UserResponse} (incl. names, phone, avatarUrl) under
 * the user id; every committed account-settings change drops that entry so admin views never show
 * a stale profile or avatar.
 */
@Component
@RequiredArgsConstructor
public class UserCacheEvictionListener {

    static final String USERS_CACHE = "users";

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AuditRecorded event) {
        Cache cache = cacheManager.getCache(USERS_CACHE);
        if (cache != null && event.change().resourceId() != null) {
            cache.evict(event.change().resourceId());
        }
    }
}
