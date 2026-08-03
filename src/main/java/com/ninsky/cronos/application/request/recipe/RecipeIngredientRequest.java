package com.ninsky.cronos.application.request.recipe;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record RecipeIngredientRequest(
        @NotNull(message = "La materia prima es obligatoria")
        UUID rawMaterialId,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        BigDecimal quantity,

        @NotNull(message = "La unidad de medida es obligatoria")
        Long unitId,

        Integer displayOrder,
        boolean isOptional,
        String notes
) {}
