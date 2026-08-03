package com.ninsky.cronos.application.response.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record RawMaterialListResponse(
        UUID id,
        String name,
        String categoryName, // O String categoryName si ya tienes el JOIN
        String purchaseUnitCode, // Ej: "Kilogramo"
        BigDecimal purchaseQuantity,
        BigDecimal unitCost,
        BigDecimal yieldPercentage,
        BigDecimal baseUnitCost,
        RecordStatus status
) { }