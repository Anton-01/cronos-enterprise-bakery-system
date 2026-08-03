package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record SimpleRecipeResponse(
        UUID id,
        String name,
        String description,
        String primaryImageUrl,
        BigDecimal totalCost
) {}
