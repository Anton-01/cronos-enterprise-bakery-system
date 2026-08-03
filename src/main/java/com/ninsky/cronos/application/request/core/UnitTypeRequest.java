package com.ninsky.cronos.application.request.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UnitTypeRequest(
        @NotBlank(message = "UnitType - codeIdentity field is required")
        @Size(max = 10)
        String codeIdentity,

        @NotBlank(message = "UnitType - name field is required")
        @Size(max = 100)
        String name,

        @NotBlank(message = "UnitType - Dimension field is required")
        @Size(max = 100)
        String dimension
) {
    public UnitTypeRequest {
        codeIdentity = (codeIdentity != null) ? codeIdentity.trim() : null;
        name = (name != null) ? name.trim() : null;
        dimension = (dimension != null) ? dimension.trim() : null;
    }
}