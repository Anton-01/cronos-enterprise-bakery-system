package com.ninsky.cronos.application.response.core;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CategoryResponse(
        Long id,
        String name,
        String description,
        String type,
        String scope,
        String status,
        /** Null for SYSTEM categories. */
        UUID userId
) {}
