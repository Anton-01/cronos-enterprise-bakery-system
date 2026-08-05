package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.Permission;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.PermissionJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper {

    public Permission toDomain(PermissionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Permission.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .resource(entity.getResource())
                .action(entity.getAction())
                .build();
    }

    public PermissionJpaEntity toEntity(Permission domain) {
        if (domain == null) {
            return null;
        }
        return PermissionJpaEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .resource(domain.getResource())
                .action(domain.getAction())
                .build();
    }
}
