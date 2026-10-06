package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.RoleJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserJpaEntity;
import com.ninsky.cronos.infrastructure.security.crypto.BlindIndexService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    private static final String EMAIL_FIELD_CONTEXT = "email";

    private final BlindIndexService blindIndexService;

    public UserMapper(BlindIndexService blindIndexService) {
        this.blindIndexService = blindIndexService;
    }

    public User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return User.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .emailVerified(entity.isEmailVerified())
                .password(entity.getPassword())
                .roleIds(entity.getRoles().stream().map(RoleJpaEntity::getId).collect(Collectors.toSet()))
                .enabled(entity.isEnabled())
                .accountNonLocked(entity.isAccountNonLocked())
                .accountNonExpired(entity.isAccountNonExpired())
                .credentialsNonExpired(entity.isCredentialsNonExpired())
                .twoFactorEnabled(entity.isTwoFactorEnabled())
                .failedLoginAttempts(entity.getFailedLoginAttempts())
                .lockedUntil(entity.getLockedUntil())
                .lastFailedLogin(entity.getLastFailedLogin())
                .passwordChangedAt(entity.getPasswordChangedAt())
                .lastLoginAt(entity.getLastLoginAt())
                .passwordNeedsChange(entity.isPasswordNeedsChange())
                .avatarKey(entity.getAvatarKey())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Caller resolves {@code roles} (via {@code RoleRepositoryPort}) before persisting.
     * {@code createdAt}/{@code updatedAt} live on {@code AuditableEntity} and Lombok's plain
     * {@code @Builder} (not {@code @SuperBuilder}) doesn't expose superclass fields on the builder,
     * so they're set via the inherited setters after building.
     */
    public UserJpaEntity toEntity(User domain, Set<RoleJpaEntity> roles) {
        if (domain == null) {
            return null;
        }
        UserJpaEntity entity = buildEntity(domain, roles);
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private UserJpaEntity buildEntity(User domain, Set<RoleJpaEntity> roles) {
        return UserJpaEntity.builder()
                .id(domain.getId())
                .username(domain.getUsername())
                .email(domain.getEmail())
                .emailBlindIndex(blindIndexService.hmac(EMAIL_FIELD_CONTEXT, domain.getEmail()))
                .emailVerified(domain.isEmailVerified())
                .password(domain.getPassword())
                .roles(roles)
                .enabled(domain.isEnabled())
                .accountNonLocked(domain.isAccountNonLocked())
                .accountNonExpired(domain.isAccountNonExpired())
                .credentialsNonExpired(domain.isCredentialsNonExpired())
                .twoFactorEnabled(domain.isTwoFactorEnabled())
                .failedLoginAttempts(domain.getFailedLoginAttempts())
                .lockedUntil(domain.getLockedUntil())
                .lastFailedLogin(domain.getLastFailedLogin())
                .passwordChangedAt(domain.getPasswordChangedAt())
                .lastLoginAt(domain.getLastLoginAt())
                .passwordNeedsChange(domain.isPasswordNeedsChange())
                .avatarKey(domain.getAvatarKey())
                .version(domain.getVersion())
                .build();
    }
}
