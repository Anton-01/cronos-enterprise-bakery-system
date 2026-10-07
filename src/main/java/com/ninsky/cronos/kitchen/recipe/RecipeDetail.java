package com.ninsky.cronos.kitchen.recipe;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ninsky.cronos.finance.shared.UserRef;
import com.ninsky.cronos.kitchen.costing.CostStatus;
import com.ninsky.cronos.kitchen.costing.FixedCostMethod;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileResponse;
import com.ninsky.cronos.kitchen.shared.AllergenRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** §5.1 detail = summary + process, lines, fixed costs, files and cost; {@code mayContain} = optional-line allergens. */
public record RecipeDetail(@JsonUnwrapped RecipeSummary summary, String description, String processHtml, String storageInstructions,
                           Integer shelfLifeDays, Integer prepMinutes, Integer bakeMinutes, Integer coolMinutes, Integer ovenTemperatureC,
                           BigDecimal wastePercent, List<Line> lines, List<FixedCost> fixedCosts, List<RecipeFileResponse> files,
                           Cost cost, List<AllergenRef> mayContain, Instant createdAt, UserRef createdBy, UserRef updatedBy, long version) {

    public record Line(UUID id, UUID ingredientId, String ingredientName, String section, BigDecimal quantity, long unitId, String unitCode,
                       boolean optional, boolean quoteSelectable, String notes, List<LineAllergen> allergens, BigDecimal lineCost,
                       int displayOrder) {
    }

    public record LineAllergen(long allergenId, String code, String name, AllergenSource source) {
    }

    public record FixedCost(UUID id, UUID userFixedCostId, String name, FixedCostMethod method, Integer minutes, BigDecimal percentage,
                            BigDecimal cost) {
    }

    /** {@code RecipeCost}. */
    public record Cost(BigDecimal ingredientsCost, BigDecimal wasteCost, BigDecimal fixedCosts, BigDecimal totalCost, BigDecimal costPerUnit,
                       BigDecimal suggestedUnitPrice, String currency, CostStatus status, int unpricedLines, Instant calculatedAt) {
    }
}
