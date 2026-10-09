package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.PricingMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /recipes/cost-preview}: editor mode ({@code lines} given) or configurator mode ({@code recipeId} + configuration).
 * {@code pricingMethod} defaults to the stored recipe's, else MARKUP.
 */
public record CostPreviewRequest(UUID recipeId, List<RecipeRequest.LineRequest> lines, List<RecipeRequest.FixedCostRequest> fixedCosts,
                                 BigDecimal yieldQuantity, BigDecimal wastePercent, BigDecimal targetMarginPercent,
                                 PricingMethod pricingMethod, RecipeConfiguration configuration) {
}
