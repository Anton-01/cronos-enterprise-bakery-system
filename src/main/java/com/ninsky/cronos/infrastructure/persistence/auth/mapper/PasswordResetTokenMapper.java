package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.PasswordResetToken;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.PasswordResetTokenJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetTokenMapper {

    public PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return PasswordResetToken.builder()
                .id(entity.getId())
                .token(entity.getToken())
                .userId(entity.getUserId())
                .expiresAt(entity.getExpiresAt())
                .used(entity.isUsed())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public PasswordResetTokenJpaEntity toEntity(PasswordResetToken domain) {
        if (domain == null) {
            return null;
        }
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
        entity.setId(domain.getId());
        entity.setToken(domain.getToken());
        entity.setUserId(domain.getUserId());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setUsed(domain.isUsed());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
