package com.ninsky.cronos.application.response.core;

import lombok.Builder;

@Builder
public record CategoryResponse(
        Long id,
        String name,
        String description,
        Boolean isSystemDefault,
        String status
) {}
