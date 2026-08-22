package com.ninsky.cronos.infrastructure.persistence.core.mapper;

import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.infrastructure.persistence.core.entity.CategoryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public Category toDomain(CategoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Category.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .scope(entity.getScope())
                .userId(entity.getUserId())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .build();
    }

    public CategoryJpaEntity toEntity(Category domain) {
        if (domain == null) {
            return null;
        }
        return CategoryJpaEntity.builder()
                .id(domain.id())
                .name(domain.name())
                .description(domain.description())
                .type(domain.type())
                .scope(domain.scope())
                .userId(domain.userId())
                .version(domain.version())
                .status(domain.status())
                .build();
    }
}
