package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PublicIngredientDto(
        String name,
        BigDecimal quantity,
        String unitName,
        boolean isOptional
) {}