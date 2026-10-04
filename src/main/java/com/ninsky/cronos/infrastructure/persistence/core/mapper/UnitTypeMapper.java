package com.ninsky.cronos.infrastructure.persistence.core.mapper;

import com.ninsky.cronos.domain.model.core.UnitType;
import com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UnitTypeMapper {

    public UnitType toDomain(UnitTypeJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return UnitType.builder()
                .id(entity.getId())
                .codeIdentity(entity.getCodeIdentity())
                .name(entity.getName())
                .dimension(entity.getDimension())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    /**
     * Carries the creation audit fields across so merging an update doesn't hand back an entity
     * with {@code createdAt = null} (the column itself is {@code updatable = false} either way).
     */
    public UnitTypeJpaEntity toEntity(UnitType domain) {
        if (domain == null) {
            return null;
        }
        UnitTypeJpaEntity entity = UnitTypeJpaEntity.builder()
                .id(domain.getId())
                .codeIdentity(domain.getCodeIdentity())
                .name(domain.getName())
                .dimension(domain.getDimension())
                .status(domain.getStatus())
                .build();
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setCreatedBy(domain.getCreatedBy());
        return entity;
    }
}
