package com.ninsky.cronos.application.response.core;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code path}: IDENTITY | LINEAR | DENSITY. {@code densityRuleId} is set only for DENSITY.
 * {@code result} carries up to 10 decimals; round for display only.
 */
public record UnitConversionResponse(
        BigDecimal quantity,
        Long fromUnitId,
        String fromUnitCode,
        Long toUnitId,
        String toUnitCode,
        BigDecimal result,
        String path,
        Long densityRuleId,
        UUID rawMaterialId
) { }
