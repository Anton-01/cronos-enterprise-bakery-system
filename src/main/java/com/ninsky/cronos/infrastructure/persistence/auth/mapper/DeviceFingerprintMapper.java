package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.DeviceFingerprint;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.DeviceFingerprintJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class DeviceFingerprintMapper {

    public DeviceFingerprint toDomain(DeviceFingerprintJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return DeviceFingerprint.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .fingerprintHash(entity.getFingerprintHash())
                .deviceName(entity.getDeviceName())
                .userAgent(entity.getUserAgent())
                .browser(entity.getBrowser())
                .operatingSystem(entity.getOperatingSystem())
                .deviceType(entity.getDeviceType())
                .ipAddress(entity.getIpAddress())
                .location(entity.getLocation())
                .trusted(entity.isTrusted())
                .firstSeenAt(entity.getFirstSeenAt())
                .lastSeenAt(entity.getLastSeenAt())
                .trustedAt(entity.getTrustedAt())
                .loginCount(entity.getLoginCount())
                .build();
    }

    public DeviceFingerprintJpaEntity toEntity(DeviceFingerprint domain) {
        if (domain == null) {
            return null;
        }
        return DeviceFingerprintJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .fingerprintHash(domain.getFingerprintHash())
                .deviceName(domain.getDeviceName())
                .userAgent(domain.getUserAgent())
                .browser(domain.getBrowser())
                .operatingSystem(domain.getOperatingSystem())
                .deviceType(domain.getDeviceType())
                .ipAddress(domain.getIpAddress())
                .location(domain.getLocation())
                .trusted(domain.isTrusted())
                .firstSeenAt(domain.getFirstSeenAt())
                .lastSeenAt(domain.getLastSeenAt())
                .trustedAt(domain.getTrustedAt())
                .loginCount(domain.getLoginCount())
                .build();
    }
}
