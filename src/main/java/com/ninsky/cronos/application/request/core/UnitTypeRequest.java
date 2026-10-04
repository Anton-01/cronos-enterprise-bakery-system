package com.ninsky.cronos.application.request.core;

import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.domain.service.core.MeasurementUnitRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** Body of {@code POST /unit-type} and {@code PUT /unit-type/{id}}. Status changes go through PATCH. */
@Builder
public record UnitTypeRequest(
        @NotBlank(message = "{validation.unitType.code.required}")
        @Size(max = MeasurementUnitRules.CODE_MAX_LENGTH, message = "{validation.catalog.code.size}")
        @Pattern(regexp = MeasurementUnitRules.CODE_REGEX, message = "{validation.catalog.code.pattern}")
        String codeIdentity,

        @NotBlank(message = "{validation.unitType.name.required}")
        @Size(max = MeasurementUnitRules.NAME_MAX_LENGTH, message = "{validation.catalog.name.size}")
        String name,

        @NotNull(message = "{validation.unitType.dimension.required}")
        UnitDimension dimension
) {
    public UnitTypeRequest {
        codeIdentity = codeIdentity != null ? codeIdentity.trim() : null;
        name = name != null ? name.trim() : null;
    }
}
