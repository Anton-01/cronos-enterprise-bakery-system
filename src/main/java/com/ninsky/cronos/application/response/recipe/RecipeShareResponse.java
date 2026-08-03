package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record RecipeShareResponse(
        UUID id, String shareUrl, LocalDateTime expiresAt,
        Integer viewsCount, boolean isRevoked, LocalDateTime createdAt
) {}
