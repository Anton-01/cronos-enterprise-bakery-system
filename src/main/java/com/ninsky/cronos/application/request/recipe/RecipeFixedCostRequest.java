package com.ninsky.cronos.application.request.recipe;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record RecipeFixedCostRequest(
        @NotNull(message = "Debes seleccionar un costo del catálogo")
        UUID userFixedCostId,
        Integer timeInMinutes,
        BigDecimal percentage
) {}
