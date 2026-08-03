package com.ninsky.cronos.application.response.core;

import lombok.Builder;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record MeasurementUnitResponse(
        Long id,
        String codeIdentity,
        String name,
        String namePlural,
        String unitType,
        BigDecimal multiplierToBase,
        boolean isBaseUnit,
        boolean isSystemDefault,
        String status
) { }
