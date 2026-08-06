package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.DeviceFingerprint;

import java.util.Optional;
import java.util.UUID;

public interface DeviceFingerprintRepositoryPort {
    DeviceFingerprint save(DeviceFingerprint deviceFingerprint);
    Optional<DeviceFingerprint> findByUserIdAndFingerprintHash(UUID userId, String fingerprintHash);
}
