package com.ninsky.cronos.infrastructure.persistence.core.mapper;

import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.MeasurementUnitView;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class MeasurementUnitMapper {

    public MeasurementUnit toDomain(MeasurementUnitJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return MeasurementUnit.builder()
                .id(entity.getId())
                .codeIdentity(entity.getCodeIdentity())
                .name(entity.getName())
                .namePlural(entity.getNamePlural())
                .unitTypeId(entity.getUnitType() != null ? entity.getUnitType().getId() : null)
                .multiplierToBase(entity.getMultiplierToBase())
                .isBaseUnit(entity.isBaseUnit())
                .isSystemDefault(entity.isSystemDefault())
                .userId(entity.getUserId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    /** Requires {@code entity.unitType} to be initialized (fetched through the entity graph). */
    public MeasurementUnitView toView(MeasurementUnitJpaEntity entity) {
        UnitTypeJpaEntity unitType = entity.getUnitType();
        return new MeasurementUnitView(toDomain(entity), unitType.getCodeIdentity(), unitType.getName(), unitType.getDimension());
    }

    /**
     * Caller resolves {@code unitType} (via {@code UnitTypeRepositoryPort}) before persisting.
     * Carries the creation audit fields across, see {@link UnitTypeMapper#toEntity}.
     */
    public MeasurementUnitJpaEntity toEntity(MeasurementUnit domain, UnitTypeJpaEntity unitType) {
        if (domain == null) {
            return null;
        }
        MeasurementUnitJpaEntity entity = MeasurementUnitJpaEntity.builder()
                .id(domain.getId())
                .codeIdentity(domain.getCodeIdentity())
                .name(domain.getName())
                .namePlural(domain.getNamePlural())
                .unitType(unitType)
                .multiplierToBase(domain.getMultiplierToBase())
                .isBaseUnit(domain.isBaseUnit())
                .isSystemDefault(domain.isSystemDefault())
                .userId(domain.getUserId())
                .status(domain.getStatus())
                .build();
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setCreatedBy(domain.getCreatedBy());
        return entity;
    }
}
