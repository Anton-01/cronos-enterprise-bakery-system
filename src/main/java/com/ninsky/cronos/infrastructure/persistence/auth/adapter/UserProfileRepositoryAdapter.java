package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.UserProfileJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.UserProfileMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserProfileRepositoryAdapter implements UserProfileRepositoryPort {

    private final UserProfileJpaRepository jpaRepository;
    private final UserProfileMapper mapper;

    public UserProfileRepositoryAdapter(UserProfileJpaRepository jpaRepository, UserProfileMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public UserProfile save(UserProfile profile) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(profile)));
    }

    @Override
    public Optional<UserProfile> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).map(mapper::toDomain);
    }
}
