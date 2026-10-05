package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.iam.access.AccessCaches;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * The {@code userAuth} projection (password hash, 2FA flag, status) is cached by login id; credential
 * changes drop it once committed so the next request sees them.
 */
@Component
@RequiredArgsConstructor
public class AuthProjectionCache {

    private final CacheManager cacheManager;

    public void evictNow() {
        Optional.ofNullable(cacheManager.getCache(AccessCaches.USER_AUTH)).ifPresent(Cache::clear);
    }

    public void evictAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictNow();
            }
        });
    }
}
