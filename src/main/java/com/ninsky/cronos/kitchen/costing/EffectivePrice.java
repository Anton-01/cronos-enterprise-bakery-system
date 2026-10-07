package com.ninsky.cronos.kitchen.costing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** The price an ingredient is costed with for a tenant: own latest, else platform reference. */
public record EffectivePrice(UUID ingredientId, BigDecimal costPerBaseUnit, LocalDate pricedAt, PriceSource source) {

    public static EffectivePrice none(UUID ingredientId) {
        return new EffectivePrice(ingredientId, null, null, PriceSource.NONE);
    }

    public boolean stale(LocalDate today, int staleDays) {
        return pricedAt != null && pricedAt.isBefore(today.minusDays(staleDays));
    }
}
