package com.ninsky.cronos.quote.infrastructure;

import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.quote.application.QuoteLifecycleService;
import com.ninsky.cronos.quote.domain.QuoteEvents;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Evicts the owner's cached quote figures ({@code GET /quotes/stats}, {@code GET /dashboard/summary}) on every quote
 * event (performance-latency P1-5). Eviction is idempotent, so redelivery is harmless; other instances converge within
 * the 30-second TTL.
 */
@Configuration
public class QuoteStatsInvalidation {

    /** Cache of {@code GET /dashboard/summary}, also keyed by owner. */
    public static final String DASHBOARD_CACHE = "dashboardSummary";

    private final CacheManager cacheManager;

    public QuoteStatsInvalidation(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteCreated> quoteCreatedStats() {
        return evicting(QuoteEvents.QuoteCreated.class, "QuoteCreatedStats", QuoteEvents.QuoteCreated::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteUpdated> quoteUpdatedStats() {
        return evicting(QuoteEvents.QuoteUpdated.class, "QuoteUpdatedStats", QuoteEvents.QuoteUpdated::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteSent> quoteSentStats() {
        return evicting(QuoteEvents.QuoteSent.class, "QuoteSentStats", QuoteEvents.QuoteSent::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteViewed> quoteViewedStats() {
        return evicting(QuoteEvents.QuoteViewed.class, "QuoteViewedStats", QuoteEvents.QuoteViewed::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteAccepted> quoteAcceptedStats() {
        return evicting(QuoteEvents.QuoteAccepted.class, "QuoteAcceptedStats", QuoteEvents.QuoteAccepted::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteRejected> quoteRejectedStats() {
        return evicting(QuoteEvents.QuoteRejected.class, "QuoteRejectedStats", QuoteEvents.QuoteRejected::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteExpired> quoteExpiredStats() {
        return evicting(QuoteEvents.QuoteExpired.class, "QuoteExpiredStats", QuoteEvents.QuoteExpired::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteRevoked> quoteRevokedStats() {
        return evicting(QuoteEvents.QuoteRevoked.class, "QuoteRevokedStats", QuoteEvents.QuoteRevoked::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteDuplicated> quoteDuplicatedStats() {
        return evicting(QuoteEvents.QuoteDuplicated.class, "QuoteDuplicatedStats", QuoteEvents.QuoteDuplicated::ownerId);
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteDeleted> quoteDeletedStats() {
        return evicting(QuoteEvents.QuoteDeleted.class, "QuoteDeletedStats", QuoteEvents.QuoteDeleted::ownerId);
    }

    private <E extends DomainEvent> OutboxHandler<E> evicting(Class<E> type, String name, Function<E, UUID> owner) {
        return OutboxHandlers.of(type, name, (event, envelope) -> evict(owner.apply(event)));
    }

    private void evict(UUID owner) {
        if (owner == null) {
            return;
        }
        Optional.ofNullable(cacheManager.getCache(QuoteLifecycleService.STATS_CACHE)).ifPresent(cache -> cache.evict(owner));
        Optional.ofNullable(cacheManager.getCache(DASHBOARD_CACHE)).ifPresent(cache -> cache.evict(owner));
    }
}
