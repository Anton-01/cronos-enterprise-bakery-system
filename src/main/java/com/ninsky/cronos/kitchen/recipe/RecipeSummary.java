package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.CostStatus;
import com.ninsky.cronos.kitchen.costing.PricingMethod;
import com.ninsky.cronos.kitchen.shared.AllergenRef;
import com.ninsky.cronos.kitchen.shared.Scope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** §5.1 list row; {@code allergens} = contains (required lines only). */
public record RecipeSummary(UUID id, String code, String name, Long categoryId, String categoryName, Difficulty difficulty,
                            BigDecimal yieldQuantity, String yieldUnit, RecipeStatus status, Scope scope, List<AllergenRef> allergens,
                            String coverImageUrl, BigDecimal costPerUnit, BigDecimal suggestedUnitPrice, BigDecimal targetMarginPercent,
                            PricingMethod pricingMethod, CostStatus costStatus, Instant costCalculatedAt, Integer totalMinutes, Instant updatedAt) {
}
