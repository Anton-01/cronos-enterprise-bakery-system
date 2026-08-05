package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.RefreshToken;
import com.ninsky.cronos.domain.port.auth.RefreshTokenRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.RefreshTokenJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.RefreshTokenMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepositoryPort {

    private final RefreshTokenJpaRepository jpaRepository;
    private final RefreshTokenMapper mapper;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpaRepository, RefreshTokenMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(refreshToken)));
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return jpaRepository.findByToken(token).map(mapper::toDomain);
    }

    @Override
    public void revokeAllUserTokens(UUID userId, LocalDateTime now) {
        jpaRepository.revokeAllUserTokens(userId, now);
    }

    @Override
    public void revokeTokensBySessionId(UUID sessionId) {
        jpaRepository.revokeTokensBySessionId(sessionId);
    }
}
