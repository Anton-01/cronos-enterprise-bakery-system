package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record RecipeFixedCostDto(
        UUID id,
        String userFixedCostName,
        String description,
        String type,
        BigDecimal defaultAmount,
        String calculationMethod,
        Integer timeInMinutes,
        BigDecimal percentage,
        BigDecimal calculatedCost,
        boolean isActive
) {}
