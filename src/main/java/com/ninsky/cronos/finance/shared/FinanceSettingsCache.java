package com.ninsky.cronos.finance.shared;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The {@code financeSettings} cache (defaults + calculation rules). Evicted after commit so a
 * concurrent reader cannot re-cache the pre-commit state.
 */
@Component
public class FinanceSettingsCache {

    public static final String CACHE_NAME = "financeSettings";
    private static final String KEY = "current";

    private final Cache cache;

    public FinanceSettingsCache(CacheManager cacheManager) {
        this.cache = Objects.requireNonNull(cacheManager.getCache(CACHE_NAME), "Cache " + CACHE_NAME + " is not configured");
    }

    public <T> T get(Class<T> type, Supplier<T> loader) {
        T cached = cache.get(KEY, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        cache.put(KEY, loaded);
        return loaded;
    }

    /** Evicts now and again after the surrounding transaction commits. */
    public void evict() {
        cache.evict(KEY);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.evict(KEY);
                }
            });
        }
    }
}
