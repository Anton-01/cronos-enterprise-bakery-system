package com.ninsky.cronos.application.request.recipe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CreateRecipeRequest(
        @NotBlank(message = "El nombre de la receta es obligatorio")
        String name,

        String description,
        UUID categoryId,

        @NotNull(message = "El rendimiento es obligatorio")
        @Positive(message = "El rendimiento debe ser mayor a cero")
        BigDecimal yieldQuantity,

        @NotBlank(message = "La unidad de rendimiento es obligatoria (ej. Piezas, Kg)")
        String yieldUnit,

        Integer preparationTimeMinutes,
        Integer bakingTimeMinutes,
        Integer coolingTimeMinutes,
        String instructions,
        String storageInstructions,
        Integer shelfLifeDays
) {}
