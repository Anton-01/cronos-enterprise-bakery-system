package com.ninsky.cronos.infrastructure.persistence.recipe.mapper;

import com.ninsky.cronos.domain.model.recipe.RecipeShareAccessLog;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeShareAccessLogJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RecipeShareAccessLogMapper {

    public RecipeShareAccessLog toDomain(RecipeShareAccessLogJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return RecipeShareAccessLog.builder()
                .id(entity.getId())
                .recipeShareId(entity.getRecipeShareId())
                .accessedAt(entity.getAccessedAt())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .build();
    }

    public RecipeShareAccessLogJpaEntity toEntity(RecipeShareAccessLog domain) {
        if (domain == null) {
            return null;
        }
        return RecipeShareAccessLogJpaEntity.builder()
                .id(domain.getId())
                .recipeShareId(domain.getRecipeShareId())
                .accessedAt(domain.getAccessedAt())
                .ipAddress(domain.getIpAddress())
                .userAgent(domain.getUserAgent())
                .build();
    }
}
