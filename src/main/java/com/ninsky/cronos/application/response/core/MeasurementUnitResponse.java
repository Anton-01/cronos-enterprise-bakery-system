package com.ninsky.cronos.application.response.core;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code unitType} keeps carrying the unit type's display name (pre-existing contract);
 * {@code unitTypeId}/{@code unitTypeCode}/{@code dimension} are what an edit form needs.
 * {@code inUse}: referenced by raw materials or recipes, so {@code unitTypeId},
 * {@code multiplierToBase} and {@code isBaseUnit} are frozen and the unit cannot be deleted.
 */
@Builder
public record MeasurementUnitResponse(
        Long id,
        String codeIdentity,
        String name,
        String namePlural,
        Long unitTypeId,
        String unitTypeCode,
        String unitType,
        String dimension,
        BigDecimal multiplierToBase,
        boolean isBaseUnit,
        boolean isSystemDefault,
        boolean inUse,
        String status,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) { }
