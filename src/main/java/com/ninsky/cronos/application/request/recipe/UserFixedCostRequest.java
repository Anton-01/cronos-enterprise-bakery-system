package com.ninsky.cronos.application.request.recipe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * {@code POST/PUT /user-fixed-cost}. New fields are optional (B1): {@code appliesByDefault} defaults to false;
 * {@code monthlyAmount}/{@code monthlyBasis} come together and record how {@code defaultAmount} was derived
 * (it stays the authoritative value).
 */
@Builder
public record UserFixedCostRequest(
        @NotBlank(message = "El nombre del costo fijo es obligatorio")
        @Size(max = 255)
        String name,

        @Size(max = 500)
        String description,

        @NotBlank(message = "El tipo de costo es obligatorio (ej. LABOR, UTILITY, PACKAGING)")
        @Size(max = 50)
        String type,

        @PositiveOrZero(message = "El monto no puede ser negativo")
        BigDecimal defaultAmount,

        @PositiveOrZero(message = "El porcentaje no puede ser negativo")
        BigDecimal percentage,

        @NotBlank(message = "El método de cálculo es obligatorio (ej. HOURLY_RATE, PER_UNIT, FIXED_PER_BATCH, PERCENTAGE)")
        String calculationMethod,

        Boolean appliesByDefault,

        BigDecimal monthlyAmount,

        BigDecimal monthlyBasis
) {}
