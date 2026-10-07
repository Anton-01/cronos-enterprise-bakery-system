package com.ninsky.cronos.kitchen.shared;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Kitchen read caches (Caffeine, per instance). Writers evict now and again after commit, so a
 * concurrent reader cannot re-cache pre-commit state.
 * <ul>
 *   <li>{@code kitchenUnits}: unit catalog, read on every cost computation.</li>
 *   <li>{@code kitchenAllergens}: visible allergens + compiled detector, per tenant.</li>
 *   <li>{@code kitchenStats}: list headers ({@code /ingredients/stats}, {@code /recipes/stats}), per tenant.</li>
 * </ul>
 */
@Component
public class KitchenCaches {

    public static final String UNITS = "kitchenUnits";
    public static final String ALLERGENS = "kitchenAllergens";
    public static final String STATS = "kitchenStats";

    private final CacheManager cacheManager;

    /** {@code kitchenStats} key: {@code kind} is "ingredients" or "recipes". */
    public static String statsKey(String kind, java.util.UUID tenantId) {
        return kind + ':' + tenantId;
    }

    public KitchenCaches(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, Object key, Supplier<T> loader) {
        Cache cache = cache(cacheName);
        Cache.ValueWrapper hit = cache.get(key);
        if (hit != null) {
            return (T) hit.get();
        }
        T loaded = loader.get();
        cache.put(key, loaded);
        return loaded;
    }

    public void evict(String cacheName, Object key) {
        afterCommit(() -> cache(cacheName).evict(key));
    }

    public void clear(String cacheName) {
        afterCommit(() -> cache(cacheName).clear());
    }

    private static void afterCommit(Runnable action) {
        action.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        }
    }

    private Cache cache(String name) {
        return Objects.requireNonNull(cacheManager.getCache(name), "Cache " + name + " is not configured");
    }
}
