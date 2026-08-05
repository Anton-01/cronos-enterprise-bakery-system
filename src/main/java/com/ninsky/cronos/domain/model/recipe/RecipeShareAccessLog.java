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
public class RecipeShareAccessLog {
    private UUID id;
    private UUID recipeShareId;
    private LocalDateTime accessedAt;
    private String ipAddress;
    private String userAgent;
}
