package com.ninsky.cronos.infrastructure.persistence.recipe.mapper;

import com.ninsky.cronos.domain.model.recipe.RecipeShare;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeShareJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RecipeShareMapper {

    public RecipeShare toDomain(RecipeShareJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return RecipeShare.builder()
                .id(entity.getId())
                .recipeId(entity.getRecipeId())
                .userId(entity.getUserId())
                .shareToken(entity.getShareToken())
                .recipientEmail(entity.getRecipientEmail())
                .expiresAt(entity.getExpiresAt())
                .viewsCount(entity.getViewsCount())
                .isRevoked(entity.isRevoked())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public RecipeShareJpaEntity toEntity(RecipeShare domain) {
        if (domain == null) {
            return null;
        }
        return RecipeShareJpaEntity.builder()
                .id(domain.getId())
                .recipeId(domain.getRecipeId())
                .userId(domain.getUserId())
                .shareToken(domain.getShareToken())
                .recipientEmail(domain.getRecipientEmail())
                .expiresAt(domain.getExpiresAt())
                .viewsCount(domain.getViewsCount())
                .isRevoked(domain.isRevoked())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
