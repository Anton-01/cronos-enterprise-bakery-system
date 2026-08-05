package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.PasswordResetToken;
import com.ninsky.cronos.domain.port.auth.PasswordResetTokenRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.PasswordResetTokenJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.PasswordResetTokenMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepositoryPort {

    private final PasswordResetTokenJpaRepository jpaRepository;
    private final PasswordResetTokenMapper mapper;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository jpaRepository, PasswordResetTokenMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(token)));
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return jpaRepository.findByToken(token).map(mapper::toDomain);
    }
}
