package com.ninsky.cronos.application.response.core;

import lombok.Builder;

@Builder
public record UnitTypeResponse(
        Long id,
        String codeIdentity,
        String name,
        String dimension,
        String status
) { }
