package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record RecipeDetailResponse(
        UUID id,
        String name,
        String description,
        UUID categoryId,
        BigDecimal yieldQuantity,
        String yieldUnit,
        Integer preparationTimeMinutes,
        Integer bakingTimeMinutes,
        Integer coolingTimeMinutes,
        String instructions,
        String storageInstructions,
        Integer shelfLifeDays,
        String status,
        boolean isActive,
        boolean needsRecalculation,
        Integer currentVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        List<RecipeIngredientDto> ingredients,
        List<RecipeFixedCostDto> fixedCosts
) {}