package com.ninsky.cronos.application.request.core;

import com.ninsky.cronos.domain.entity.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        @NotNull(message = "Category type is required (PRODUCT or INGREDIENT)")
        CategoryType type
) {}
