package com.ninsky.cronos.application.request.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CreateMeasurementUnitRequest(
        @NotBlank(message = "MeasurementUnit - codeIdentity field is required")
        @Size(max = 10)
        String codeIdentity,

        @NotBlank(message = "MeasurementUnit - name field is required")
        @Size(max = 100)
        String name,

        @NotBlank(message = "MeasurementUnit - namePlural field is required")
        @Size(max = 100)
        String namePlural,

        @NotNull(message = "MeasurementUnit - unitTypeId field is required")
        @Positive(message = "MeasurementUnit - unitTypeId field must be greater than 0")
        Long unitTypeId,

        @NotNull(message = "MeasurementUnit - multiplierToBase field is required")
        BigDecimal multiplierToBase,

        @NotNull(message = "MeasurementUnit - isBaseUnit field is required")
        Boolean isBaseUnit,

        @Positive(message = "MeasurementUnit - unitTypeId field must be greater than 0")
        Long userId
) { }
