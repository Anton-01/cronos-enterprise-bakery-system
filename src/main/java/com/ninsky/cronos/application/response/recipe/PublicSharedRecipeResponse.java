package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record PublicSharedRecipeResponse(
        String recipeName,
        String description,
        BigDecimal yieldQuantity,
        String yieldUnit,
        Integer preparationTimeMinutes,
        Integer bakingTimeMinutes,
        String instructions,
        String storageInstructions,
        PublicOwnerDto owner,
        LocalDateTime expiresAt,
        List<PublicIngredientDto> ingredients,
        List<PublicFileDto> files
) {}
