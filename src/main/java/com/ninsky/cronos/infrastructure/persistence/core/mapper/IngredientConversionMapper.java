package com.ninsky.cronos.infrastructure.persistence.core.mapper;

import com.ninsky.cronos.domain.model.core.IngredientConversion;
import com.ninsky.cronos.infrastructure.persistence.core.entity.IngredientConversionJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class IngredientConversionMapper {

    public IngredientConversion toDomain(IngredientConversionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return IngredientConversion.builder()
                .id(entity.getId())
                .ingredientId(entity.getIngredientId())
                .volumeUnitId(entity.getVolumeUnit() != null ? entity.getVolumeUnit().getId() : null)
                .massUnitId(entity.getMassUnit() != null ? entity.getMassUnit().getId() : null)
                .factor(entity.getFactor())
                .userId(entity.getUserId())
                .build();
    }

    /** Caller resolves {@code volumeUnit}/{@code massUnit} (via {@code MeasurementUnitRepositoryPort}) before persisting. */
    public IngredientConversionJpaEntity toEntity(IngredientConversion domain, MeasurementUnitJpaEntity volumeUnit, MeasurementUnitJpaEntity massUnit) {
        if (domain == null) {
            return null;
        }
        IngredientConversionJpaEntity entity = new IngredientConversionJpaEntity();
        entity.setId(domain.getId());
        entity.setIngredientId(domain.getIngredientId());
        entity.setVolumeUnit(volumeUnit);
        entity.setMassUnit(massUnit);
        entity.setFactor(domain.getFactor());
        entity.setUserId(domain.getUserId());
        return entity;
    }
}
