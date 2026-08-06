package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.SecurityNotification;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.SecurityNotificationJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SecurityNotificationMapper {

    public SecurityNotification toDomain(SecurityNotificationJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return SecurityNotification.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .type(entity.getType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .severity(entity.getSeverity())
                .deviceName(entity.getDeviceName())
                .ipAddress(entity.getIpAddress())
                .location(entity.getLocation())
                .browser(entity.getBrowser())
                .operatingSystem(entity.getOperatingSystem())
                .read(entity.isRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .emailSent(entity.isEmailSent())
                .emailSentAt(entity.getEmailSentAt())
                .build();
    }

    public SecurityNotificationJpaEntity toEntity(SecurityNotification domain) {
        if (domain == null) {
            return null;
        }
        return SecurityNotificationJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .type(domain.getType())
                .title(domain.getTitle())
                .message(domain.getMessage())
                .severity(domain.getSeverity())
                .deviceName(domain.getDeviceName())
                .ipAddress(domain.getIpAddress())
                .location(domain.getLocation())
                .browser(domain.getBrowser())
                .operatingSystem(domain.getOperatingSystem())
                .read(domain.isRead())
                .readAt(domain.getReadAt())
                .createdAt(domain.getCreatedAt())
                .emailSent(domain.isEmailSent())
                .emailSentAt(domain.getEmailSentAt())
                .build();
    }
}
