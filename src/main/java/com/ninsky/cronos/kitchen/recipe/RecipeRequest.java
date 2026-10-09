package com.ninsky.cronos.kitchen.recipe;

import com.ninsky.cronos.kitchen.costing.PricingMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST/PUT /recipes}: the whole aggregate (§5.3); validated at once by {@link RecipeValidator}.
 * {@code pricingMethod} is optional: absent keeps the stored value (MARKUP for new recipes).
 */
public record RecipeRequest(String code, String name, Long categoryId, Difficulty difficulty, String description,
                            String storageInstructions, BigDecimal yieldQuantity, String yieldUnit, Integer prepMinutes, Integer bakeMinutes,
                            Integer coolMinutes, Integer ovenTemperatureC, Integer shelfLifeDays, String processHtml,
                            BigDecimal targetMarginPercent, PricingMethod pricingMethod, BigDecimal wastePercent, List<LineRequest> lines,
                            List<FixedCostRequest> fixedCosts, Long version) {

    public RecipeRequest {
        lines = lines == null ? List.of() : lines;
        fixedCosts = fixedCosts == null ? List.of() : fixedCosts;
    }

    public record LineRequest(UUID id, UUID ingredientId, String section, BigDecimal quantity, Long unitId, boolean optional,
                              boolean quoteSelectable, String notes, Integer displayOrder, List<ExtraAllergenRequest> extraAllergens) {
        public LineRequest {
            extraAllergens = extraAllergens == null ? List.of() : extraAllergens;
        }
    }

    public record ExtraAllergenRequest(Long allergenId, AllergenSource source) {
    }

    /** {@code quantity}: PER_UNIT units per batch (null = one per yield unit); must be null for other methods. */
    public record FixedCostRequest(UUID userFixedCostId, Integer minutes, BigDecimal percentage, BigDecimal quantity) {
    }
}
