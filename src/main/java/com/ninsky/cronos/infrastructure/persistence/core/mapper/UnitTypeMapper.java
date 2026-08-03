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
                .build();
    }

    public UnitTypeJpaEntity toEntity(UnitType domain) {
        if (domain == null) {
            return null;
        }
        return UnitTypeJpaEntity.builder()
                .id(domain.getId())
                .codeIdentity(domain.getCodeIdentity())
                .name(domain.getName())
                .dimension(domain.getDimension())
                .status(domain.getStatus())
                .build();
    }
}
