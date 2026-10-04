package com.ninsky.cronos.application.response.core;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UnitTypeResponse(
        Long id,
        String codeIdentity,
        String name,
        String dimension,
        String status,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) { }
