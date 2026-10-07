package com.ninsky.cronos.iam.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/** Policy read through the short-lived {@code securityPolicy} cache; dropped once a change commits. */
@Component
@RequiredArgsConstructor
public class CachedSecurityPolicyProvider implements SecurityPolicyProvider {

    static final String CACHE = "securityPolicy";
    private static final String KEY = "current";

    private final SecurityPolicyCustomRepository store;
    private final CacheManager cacheManager;

    @Override
    @Cacheable(value = CACHE, key = "'" + KEY + "'")
    public SecurityPolicy current() {
        return store.load();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(SecurityPolicyChanged event) {
        Optional.ofNullable(cacheManager.getCache(CACHE)).ifPresent(cache -> cache.evict(KEY));
    }
}
