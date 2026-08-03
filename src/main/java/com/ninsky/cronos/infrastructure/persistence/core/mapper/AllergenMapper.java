package com.ninsky.cronos.infrastructure.persistence.core.mapper;

import com.ninsky.cronos.domain.model.core.Allergen;
import com.ninsky.cronos.infrastructure.persistence.core.entity.AllergenJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class AllergenMapper {

    public Allergen toDomain(AllergenJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Allergen.builder()
                .id(entity.getId())
                .name(entity.getName())
                .alternativeName(entity.getAlternativeName())
                .description(entity.getDescription())
                .isSystemDefault(entity.getIsSystemDefault())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .build();
    }

    public AllergenJpaEntity toEntity(Allergen domain) {
        if (domain == null) {
            return null;
        }
        return AllergenJpaEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .alternativeName(domain.getAlternativeName())
                .description(domain.getDescription())
                .isSystemDefault(domain.getIsSystemDefault())
                .version(domain.getVersion())
                .status(domain.getStatus())
                .build();
    }
}
