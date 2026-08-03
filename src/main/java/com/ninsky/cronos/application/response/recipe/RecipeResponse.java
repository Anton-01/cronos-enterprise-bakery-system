package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record RecipeResponse(
        UUID id,
        String name,
        String description,
        BigDecimal yieldQuantity,
        String yieldUnit,
        String status,
        boolean isActive,
        boolean needsRecalculation,
        Integer currentVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
