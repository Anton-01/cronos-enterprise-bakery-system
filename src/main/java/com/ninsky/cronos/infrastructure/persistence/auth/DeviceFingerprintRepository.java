package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.domain.entity.auth.DeviceFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceFingerprintRepository extends JpaRepository<DeviceFingerprint, UUID> {

    // Spring Data crea la consulta SQL automáticamente basándose en este nombre
    Optional<DeviceFingerprint> findByUserIdAndFingerprintHash(UUID userId, String fingerprintHash);
}
