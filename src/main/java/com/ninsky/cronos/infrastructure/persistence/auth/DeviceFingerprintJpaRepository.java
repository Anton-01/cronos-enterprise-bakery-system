package com.ninsky.cronos.infrastructure.persistence.auth;

import com.ninsky.cronos.infrastructure.persistence.auth.entity.DeviceFingerprintJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceFingerprintJpaRepository extends JpaRepository<DeviceFingerprintJpaEntity, UUID> {
    Optional<DeviceFingerprintJpaEntity> findByUserIdAndFingerprintHash(UUID userId, String fingerprintHash);
}
