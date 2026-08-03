package com.ninsky.cronos.application.response.core;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AllergenResponse(
        UUID id,
        String name,
        String alternativeName,
        String description,
        Boolean isSystemDefault,
        String status
) { }
