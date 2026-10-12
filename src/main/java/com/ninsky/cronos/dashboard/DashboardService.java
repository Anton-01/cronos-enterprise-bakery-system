package com.ninsky.cronos.dashboard;

import com.ninsky.cronos.iam.shared.ActorProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * {@code GET /dashboard/summary}: one statement, cached 30 s per user ({@code dashboardSummary}). Quote events and
 * kitchen/fixed-cost writes evict the user's entry; other instances converge within the TTL.
 */
@Service
public class DashboardService {

    /** Cache name, keyed by user id. */
    public static final String CACHE = "dashboardSummary";

    private final DashboardCustomRepository repository;
    private final ActorProvider actors;
    private final CacheManager cacheManager;

    public DashboardService(DashboardCustomRepository repository, ActorProvider actors, CacheManager cacheManager) {
        this.repository = repository;
        this.actors = actors;
        this.cacheManager = cacheManager;
    }

    public DashboardSummary summary() {
        UUID owner = actors.require().id();
        Cache cache = cacheManager.getCache(CACHE);
        return cache == null ? repository.summary(owner) : cache.get(owner, () -> repository.summary(owner));
    }
}
