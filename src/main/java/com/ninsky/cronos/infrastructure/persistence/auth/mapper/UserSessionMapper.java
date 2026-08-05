package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.UserSession;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserSessionJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserSessionMapper {

    public UserSession toDomain(UserSessionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserSession.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .sessionToken(entity.getSessionToken())
                .deviceId(entity.getDeviceId())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .browser(entity.getBrowser())
                .operatingSystem(entity.getOperatingSystem())
                .device(entity.getDevice())
                .location(entity.getLocation())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .lastActivityAt(entity.getLastActivityAt())
                .expiresAt(entity.getExpiresAt())
                .terminatedAt(entity.getTerminatedAt())
                .terminationReason(entity.getTerminationReason())
                .build();
    }

    public UserSessionJpaEntity toEntity(UserSession domain) {
        if (domain == null) {
            return null;
        }
        return UserSessionJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .sessionToken(domain.getSessionToken())
                .deviceId(domain.getDeviceId())
                .ipAddress(domain.getIpAddress())
                .userAgent(domain.getUserAgent())
                .browser(domain.getBrowser())
                .operatingSystem(domain.getOperatingSystem())
                .device(domain.getDevice())
                .location(domain.getLocation())
                .isActive(domain.isActive())
                .createdAt(domain.getCreatedAt())
                .lastActivityAt(domain.getLastActivityAt())
                .expiresAt(domain.getExpiresAt())
                .terminatedAt(domain.getTerminatedAt())
                .terminationReason(domain.getTerminationReason())
                .build();
    }
}
