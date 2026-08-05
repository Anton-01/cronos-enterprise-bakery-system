package com.ninsky.cronos.domain.model.recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeShare {
    private UUID id;
    private UUID recipeId;
    private UUID userId;
    private String shareToken;
    private String recipientEmail;
    private LocalDateTime expiresAt;
    @Builder.Default
    private Integer viewsCount = 0;
    @Builder.Default
    private boolean isRevoked = false;
    private LocalDateTime createdAt;
}
