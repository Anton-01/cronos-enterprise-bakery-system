package com.ninsky.cronos.application.request.core;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateRawMaterialRequest(

        @NotBlank(message = "El nombre del insumo es obligatorio.")
        @Size(max = 200, message = "El nombre no puede exceder los 200 caracteres.")
        String name,

        String description,

        @Size(max = 255, message = "La marca no puede exceder los 255 caracteres.")
        String brand,

        @Size(max = 500, message = "El proveedor no puede exceder los 500 caracteres.")
        String supplier,

        @NotNull(message = "La categoría es obligatoria.")
        Long categoryId,

        @NotNull(message = "La unidad de compra es obligatoria.")
        Long purchaseUnitId,

        @NotNull(message = "La cantidad de compra es obligatoria.")
        @Positive(message = "La cantidad de compra debe ser mayor a cero.")
        BigDecimal purchaseQuantity,

        @NotNull(message = "El costo es obligatorio.")
        @PositiveOrZero(message = "El costo no puede ser negativo.")
        BigDecimal unitCost,

        @NotBlank(message = "La moneda es obligatoria.")
        @Size(min = 3, max = 3, message = "La moneda debe tener exactamente 3 caracteres (ej. MXN).")
        String currency,

        @NotNull(message = "El porcentaje de rendimiento es obligatorio.")
        @DecimalMin(value = "0.01", message = "El rendimiento debe ser mayor a 0.")
        @DecimalMax(value = "100.00", message = "El rendimiento no puede exceder 100%.")
        BigDecimal yieldPercentage,

        @PositiveOrZero(message = "El stock mínimo no puede ser negativo.")
        BigDecimal minimumStock,

        DensityConversionRequest densityConversion
) {}