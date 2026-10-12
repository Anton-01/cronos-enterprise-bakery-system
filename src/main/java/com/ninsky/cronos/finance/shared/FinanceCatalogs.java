package com.ninsky.cronos.finance.shared;

import com.ninsky.cronos.finance.taxrate.TaxRateOption;
import com.ninsky.cronos.finance.taxrate.TaxRateQueryCustomRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Cached picker catalogs of the finance module (performance-latency P1-1). Reference data: read on almost every
 * screen, changed rarely. Every currency or tax-rate write evicts the whole cache; other instances converge within the
 * cache TTL ({@code CacheConfig}).
 */
@Component
public class FinanceCatalogs {

    /** Cache name; entries {@code currencies} and {@code taxRates:{day}}. */
    public static final String CACHE = "financeCatalogs";

    private final TaxRateQueryCustomRepository taxRates;

    public FinanceCatalogs(TaxRateQueryCustomRepository taxRates) {
        this.taxRates = taxRates;
    }

    /** Tax rates selectable on {@code day}. */
    @Cacheable(cacheNames = CACHE, key = "'taxRates:' + #day")
    public List<TaxRateOption> taxRates(LocalDate day) {
        return taxRates.catalog(day);
    }

    /** Drops every cached finance catalog. */
    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public void evictAll() {
        // Eviction is done by the annotation.
    }
}
