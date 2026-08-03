package com.ninsky.cronos.application.request.core;

import com.ninsky.cronos.application.response.core.DensityConversionDto;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record RawMaterialResponse(
        UUID id,
        String name,
        Long categoryId,
        String description,
        String brand,
        String supplier,
        Long purchaseUnitId,
        BigDecimal purchaseQuantity,
        BigDecimal unitCost,
        String currency,
        BigDecimal yieldPercentage,
        BigDecimal minimumStock,
        BigDecimal baseUnitCost,
        String status,
        DensityConversionDto densityConversion
) { }
