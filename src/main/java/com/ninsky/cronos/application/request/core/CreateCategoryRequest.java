package com.ninsky.cronos.application.request.core;

import com.ninsky.cronos.domain.entity.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateCategoryRequest(
        @NotBlank(message = "{validation.category.name.required}")
        @Size(max = 100)
        String name,

        /** Optional by design — {@code CategoryServiceImplementation} treats a null/blank
         *  description as legitimate input, not a data error. */
        @Size(max = 500)
        String description,

        @NotNull(message = "{validation.category.type.required}")
        CategoryType type
) {}
