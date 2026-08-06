package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.LoginHistory;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.LoginHistoryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class LoginHistoryMapper {

    public LoginHistory toDomain(LoginHistoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LoginHistory.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .loginAt(entity.getLoginAt())
                .ipAddress(entity.getIpAddress())
                .status(entity.getStatus())
                .userAgent(entity.getUserAgent())
                .browser(entity.getBrowser())
                .operatingSystem(entity.getOperatingSystem())
                .device(entity.getDevice())
                .location(entity.getLocation())
                .successful(entity.isSuccessful())
                .failureReason(entity.getFailureReason())
                .twoFactorUsed(entity.getTwoFactorUsed())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public LoginHistoryJpaEntity toEntity(LoginHistory domain) {
        if (domain == null) {
            return null;
        }
        return LoginHistoryJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .loginAt(domain.getLoginAt())
                .ipAddress(domain.getIpAddress())
                .status(domain.getStatus())
                .userAgent(domain.getUserAgent())
                .browser(domain.getBrowser())
                .operatingSystem(domain.getOperatingSystem())
                .device(domain.getDevice())
                .location(domain.getLocation())
                .successful(domain.isSuccessful())
                .failureReason(domain.getFailureReason())
                .twoFactorUsed(domain.getTwoFactorUsed())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
