package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.LoginHistory;
import com.ninsky.cronos.domain.port.auth.LoginHistoryRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.LoginHistoryJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.LoginHistoryMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class LoginHistoryRepositoryAdapter implements LoginHistoryRepositoryPort {

    private final LoginHistoryJpaRepository jpaRepository;
    private final LoginHistoryMapper mapper;

    public LoginHistoryRepositoryAdapter(LoginHistoryJpaRepository jpaRepository, LoginHistoryMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public LoginHistory save(LoginHistory loginHistory) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(loginHistory)));
    }

    @Override
    public List<LoginHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
