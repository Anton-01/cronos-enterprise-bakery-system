package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.RefreshToken;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.RefreshTokenJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenMapper {

    public RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return RefreshToken.builder()
                .id(entity.getId())
                .token(entity.getToken())
                .userId(entity.getUserId())
                .sessionId(entity.getSessionId())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .revoked(entity.isRevoked())
                .revokedAt(entity.getRevokedAt())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .build();
    }

    public RefreshTokenJpaEntity toEntity(RefreshToken domain) {
        if (domain == null) {
            return null;
        }
        return RefreshTokenJpaEntity.builder()
                .id(domain.getId())
                .token(domain.getToken())
                .userId(domain.getUserId())
                .sessionId(domain.getSessionId())
                .expiresAt(domain.getExpiresAt())
                .createdAt(domain.getCreatedAt())
                .revoked(domain.isRevoked())
                .revokedAt(domain.getRevokedAt())
                .ipAddress(domain.getIpAddress())
                .userAgent(domain.getUserAgent())
                .build();
    }
}
