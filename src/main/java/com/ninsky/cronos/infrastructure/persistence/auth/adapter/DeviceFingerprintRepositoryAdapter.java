package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.DeviceFingerprint;
import com.ninsky.cronos.domain.port.auth.DeviceFingerprintRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.DeviceFingerprintJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.DeviceFingerprintMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class DeviceFingerprintRepositoryAdapter implements DeviceFingerprintRepositoryPort {

    private final DeviceFingerprintJpaRepository jpaRepository;
    private final DeviceFingerprintMapper mapper;

    public DeviceFingerprintRepositoryAdapter(DeviceFingerprintJpaRepository jpaRepository, DeviceFingerprintMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public DeviceFingerprint save(DeviceFingerprint deviceFingerprint) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(deviceFingerprint)));
    }

    @Override
    public Optional<DeviceFingerprint> findByUserIdAndFingerprintHash(UUID userId, String fingerprintHash) {
        return jpaRepository.findByUserIdAndFingerprintHash(userId, fingerprintHash).map(mapper::toDomain);
    }
}
