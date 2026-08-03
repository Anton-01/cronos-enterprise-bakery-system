package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record RecipeCostBreakdown(
        BigDecimal targetYield,
        String yieldUnit,
        BigDecimal scaleFactor,
        BigDecimal materialsCost,
        BigDecimal fixedCosts,
        BigDecimal subRecipesCost,
        BigDecimal totalCost,
        BigDecimal costPerUnit
) {}
