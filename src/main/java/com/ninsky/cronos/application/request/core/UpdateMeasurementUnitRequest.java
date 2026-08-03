package com.ninsky.cronos.application.request.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record UpdateMeasurementUnitRequest(
        @NotBlank(message = "MeasurementUnit - id field is required")
        Long id,

        @NotBlank(message = "MeasurementUnit - codeIdentity field is required")
        @Size(max = 10)
        String codeIdentity,

        @NotBlank(message = "MeasurementUnit - name field is required")
        @Size(max = 100)
        String name,

        @NotBlank(message = "MeasurementUnit - namePlural field is required")
        @Size(max = 100)
        String namePlural,

        @NotBlank(message = "MeasurementUnit - unitTypeId field is required")
        @Positive(message = "MeasurementUnit - unitTypeId field must be greater than 0")
        Long unitTypeId,

        @NotBlank(message = "MeasurementUnit - multiplierToBase field is required")
        @Size(max = 100)
        BigDecimal multiplierToBase,

        @NotBlank(message = "MeasurementUnit - isBaseUnit field is required")
        Boolean isBaseUnit,

        @Positive(message = "MeasurementUnit - unitTypeId field must be greater than 0")
        Long userId,

        @NotBlank(message = "MeasurementUnit - Status field is required")
        @Size(max = 11)
        RecordStatus status
) { }
