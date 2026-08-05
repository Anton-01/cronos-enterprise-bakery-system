package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.Role;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.PermissionJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.RoleJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RoleMapper {

    public Role toDomain(RoleJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Role.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .permissionIds(entity.getPermissions().stream().map(PermissionJpaEntity::getId).collect(Collectors.toSet()))
                .build();
    }

    /** Caller resolves {@code permissions} (via {@code PermissionRepositoryPort}) before persisting. */
    public RoleJpaEntity toEntity(Role domain, Set<PermissionJpaEntity> permissions) {
        if (domain == null) {
            return null;
        }
        return RoleJpaEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .permissions(permissions)
                .build();
    }
}
