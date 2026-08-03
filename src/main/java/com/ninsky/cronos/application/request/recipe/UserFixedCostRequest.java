package com.ninsky.cronos.application.request.recipe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record UserFixedCostRequest(
        @NotBlank(message = "El nombre del costo fijo es obligatorio")
        String name,

        String description,

        @NotBlank(message = "El tipo de costo es obligatorio (ej. LABOR, UTILITY, PACKAGING)")
        String type,

        @PositiveOrZero(message = "El monto no puede ser negativo")
        BigDecimal defaultAmount,

        @PositiveOrZero(message = "El porcentaje no puede ser negativo")
        BigDecimal percentage,

        @NotBlank(message = "El método de cálculo es obligatorio (ej. HOURLY_RATE, PER_UNIT, FIXED_PER_BATCH, PERCENTAGE)")
        String calculationMethod
) {}
