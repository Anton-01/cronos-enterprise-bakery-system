package com.ninsky.cronos.kitchen.costing;

import com.ninsky.cronos.kitchen.shared.Dimension;

import java.math.BigDecimal;
import java.util.UUID;

/** An ingredient as the engine sees it: base dimension, density and effective cost (null = unpriced). */
public record CostIngredient(UUID id, Dimension baseDimension, BigDecimal densityGPerMl, BigDecimal costPerBaseUnit, PriceSource priceSource) {

    public boolean priced() {
        return costPerBaseUnit != null;
    }
}
