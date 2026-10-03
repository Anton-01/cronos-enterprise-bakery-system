package com.ninsky.cronos.application.listener;

import com.ninsky.cronos.application.event.UnitCatalogChangedEvent;
import com.ninsky.cronos.application.service.catalog.UnitCatalogCaches;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;
import java.util.stream.Stream;

/** Drops both unit-catalog caches after a committed change (the unit picker embeds unit-type data). */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnitCatalogCacheEvictionListener {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(UnitCatalogChangedEvent event) {
        Stream.of(UnitCatalogCaches.UNIT_TYPES, UnitCatalogCaches.MEASUREMENT_UNITS)
                .map(cacheManager::getCache)
                .filter(Objects::nonNull)
                .forEach(Cache::clear);
        log.debug("Unit catalog caches cleared ({})", event.reason());
    }
}
