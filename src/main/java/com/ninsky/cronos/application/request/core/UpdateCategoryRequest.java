package com.ninsky.cronos.application.request.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description
) { }
