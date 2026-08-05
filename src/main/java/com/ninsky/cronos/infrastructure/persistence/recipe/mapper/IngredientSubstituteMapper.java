package com.ninsky.cronos.infrastructure.persistence.recipe.mapper;

import com.ninsky.cronos.domain.model.recipe.IngredientSubstitute;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.IngredientSubstituteJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class IngredientSubstituteMapper {

    public IngredientSubstitute toDomain(IngredientSubstituteJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return IngredientSubstitute.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .originalIngredientId(entity.getOriginalIngredientId())
                .substituteMaterialId(entity.getSubstituteMaterialId())
                .conversionRatio(entity.getConversionRatio())
                .reason(entity.getReason())
                .notes(entity.getNotes())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public IngredientSubstituteJpaEntity toEntity(IngredientSubstitute domain) {
        if (domain == null) {
            return null;
        }
        IngredientSubstituteJpaEntity entity = IngredientSubstituteJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .originalIngredientId(domain.getOriginalIngredientId())
                .substituteMaterialId(domain.getSubstituteMaterialId())
                .conversionRatio(domain.getConversionRatio())
                .reason(domain.getReason())
                .notes(domain.getNotes())
                .version(domain.getVersion())
                .build();
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}
