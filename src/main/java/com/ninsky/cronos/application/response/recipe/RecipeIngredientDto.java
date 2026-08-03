package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record RecipeIngredientDto(
        UUID id,
        String rawMaterialName,
        boolean hasAllergen,
        List<String> allergenNames,
        String unitName,
        BigDecimal quantity,
        Integer displayOrder,
        boolean isOptional,
        String notes,
        BigDecimal costPerUnit,
        BigDecimal totalCost
) {}
