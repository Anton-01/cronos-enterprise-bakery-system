package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record RecipeShareAccessLogResponse(
        UUID id,
        LocalDateTime accessedAt,
        String ipAddress,
        String userAgent
) {}
