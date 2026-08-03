package com.ninsky.cronos.application.request.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record AllergenRequest(
        @NotBlank(message = "Allergen name is required")
        @Size(max = 100)
        String name,

        @NotBlank(message = "Allergen alternative name is required")
        @Size(max = 100)
        String alternativeName,

        @Size(max = 500)
        String description
) { }
