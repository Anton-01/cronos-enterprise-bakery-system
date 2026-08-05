package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.PasswordHistory;
import com.ninsky.cronos.domain.port.auth.PasswordHistoryRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.PasswordHistoryJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.PasswordHistoryMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PasswordHistoryRepositoryAdapter implements PasswordHistoryRepositoryPort {

    private final PasswordHistoryJpaRepository jpaRepository;
    private final PasswordHistoryMapper mapper;

    public PasswordHistoryRepositoryAdapter(PasswordHistoryJpaRepository jpaRepository, PasswordHistoryMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public PasswordHistory save(PasswordHistory passwordHistory) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(passwordHistory)));
    }

    @Override
    public List<PasswordHistory> findByUserIdOrderByChangedAtDesc(UUID userId, Pageable pageable) {
        return jpaRepository.findByUserIdOrderByChangedAtDesc(userId, pageable).stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
