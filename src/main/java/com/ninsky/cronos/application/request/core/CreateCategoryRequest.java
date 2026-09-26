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

        @NotBlank(message = "{validation.category.description.required}")
        @Size(max = 500)
        String description,

        @NotNull(message = "{validation.category.type.required}")
        CategoryType type
) {}
