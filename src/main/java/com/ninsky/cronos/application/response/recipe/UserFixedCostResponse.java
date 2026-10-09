package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserFixedCostResponse(
        UUID id,
        String name,
        String description,
        String type,
        BigDecimal defaultAmount,
        BigDecimal percentage,
        String calculationMethod,
        boolean isActive,
        boolean appliesByDefault,
        BigDecimal monthlyAmount,
        BigDecimal monthlyBasis,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
