package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.PriceSource;
import com.ninsky.cronos.kitchen.shared.AllergenRef;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** §5.6 response: per-line cost, per fixed-cost-row cost (baking-studio §4.4), the totals and the configured product's allergens. */
public record CostPreview(List<Line> lines, List<FixedCostRow> fixedCosts, RecipeDetail.Cost cost, List<AllergenRef> allergens) {

    public record Line(String lineKey, UUID ingredientId, BigDecimal lineCost, PriceSource priceSource) {
    }

    /** One entry per fixed-cost row at the target yield, rounded like {@code RecipeFixedCost.cost}. */
    public record FixedCostRow(UUID userFixedCostId, BigDecimal cost) {
    }
}
