package com.ninsky.cronos.application.request.core;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /measurement-unit/convert}. {@code rawMaterialId} is only needed for
 * MASS ⇄ VOLUME (it selects the ingredient's density rule) and must belong to the caller.
 */
public record UnitConversionRequest(
        @NotNull(message = "{validation.conversion.quantity.required}")
        @Positive(message = "{validation.conversion.quantity.positive}")
        @Digits(integer = 15, fraction = 10, message = "{validation.conversion.quantity.precision}")
        BigDecimal quantity,

        @NotNull(message = "{validation.conversion.fromUnitId.required}")
        Long fromUnitId,

        @NotNull(message = "{validation.conversion.toUnitId.required}")
        Long toUnitId,

        UUID rawMaterialId
) {
}
