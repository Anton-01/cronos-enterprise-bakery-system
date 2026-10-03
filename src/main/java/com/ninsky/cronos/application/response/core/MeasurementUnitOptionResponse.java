package com.ninsky.cronos.application.response.core;

import java.math.BigDecimal;

/**
 * Lightweight item of {@code GET /measurement-unit/catalog}: everything a recipe screen needs to
 * offer a unit picker and pre-compute linear conversions client-side
 * ({@code qty × from.multiplierToBase ÷ to.multiplierToBase}, same dimension only).
 */
public record MeasurementUnitOptionResponse(
        Long id,
        String codeIdentity,
        String name,
        String namePlural,
        Long unitTypeId,
        String unitTypeCode,
        String unitType,
        String dimension,
        BigDecimal multiplierToBase,
        boolean isBaseUnit
) { }
