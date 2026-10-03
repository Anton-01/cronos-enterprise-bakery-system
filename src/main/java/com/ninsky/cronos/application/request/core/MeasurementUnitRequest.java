package com.ninsky.cronos.application.request.core;

import com.ninsky.cronos.domain.service.core.MeasurementUnitRules;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Body of {@code POST /measurement-unit} and {@code PUT /measurement-unit/{id}}. Status changes go
 * through PATCH; ownership is server-decided (every unit created here is a system unit).
 * Shape constraints only — cross-record rules (one base per type, frozen in-use units, …) are
 * enforced by the service so they also apply to the .xlsx import.
 */
@Builder
public record MeasurementUnitRequest(
        @NotBlank(message = "{validation.unit.code.required}")
        @Size(max = MeasurementUnitRules.CODE_MAX_LENGTH, message = "{validation.catalog.code.size}")
        @Pattern(regexp = MeasurementUnitRules.CODE_REGEX, message = "{validation.catalog.code.pattern}")
        String codeIdentity,

        @NotBlank(message = "{validation.unit.name.required}")
        @Size(max = MeasurementUnitRules.NAME_MAX_LENGTH, message = "{validation.catalog.name.size}")
        String name,

        @NotBlank(message = "{validation.unit.namePlural.required}")
        @Size(max = MeasurementUnitRules.NAME_MAX_LENGTH, message = "{validation.catalog.name.size}")
        String namePlural,

        @NotNull(message = "{validation.unit.unitTypeId.required}")
        @Positive(message = "{validation.unit.unitTypeId.required}")
        Long unitTypeId,

        @NotNull(message = "{validation.unit.factor.required}")
        @DecimalMin(value = "0", inclusive = false, message = "{validation.unit.factor.positive}")
        @Digits(integer = MeasurementUnitRules.FACTOR_MAX_INTEGER_DIGITS, fraction = MeasurementUnitRules.FACTOR_MAX_SCALE,
                message = "{validation.unit.factor.precision}")
        BigDecimal multiplierToBase,

        @NotNull(message = "{validation.unit.isBaseUnit.required}")
        Boolean isBaseUnit
) {
    public MeasurementUnitRequest {
        codeIdentity = codeIdentity != null ? codeIdentity.trim() : null;
        name = name != null ? name.trim() : null;
        namePlural = namePlural != null ? namePlural.trim() : null;
    }
}
