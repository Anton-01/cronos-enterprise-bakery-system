package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.UserSession;
import com.ninsky.cronos.domain.port.auth.UserSessionRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.UserSessionJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.UserSessionMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UserSessionRepositoryAdapter implements UserSessionRepositoryPort {

    private final UserSessionJpaRepository jpaRepository;
    private final UserSessionMapper mapper;

    public UserSessionRepositoryAdapter(UserSessionJpaRepository jpaRepository, UserSessionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public UserSession save(UserSession session) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(session)));
    }

    @Override
    public Optional<UserSession> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public void terminateAllUserSessions(UUID userId, LocalDateTime now, String reason) {
        jpaRepository.terminateAllUserSessions(userId, now, reason);
    }

    @Override
    public List<UserSession> findByUserIdAndIsActiveTrueOrderByLastActivityAtDesc(UUID userId) {
        return jpaRepository.findByUserIdAndIsActiveTrueOrderByLastActivityAtDesc(userId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<UserSession> findByUserIdAndIsActiveTrueOrderByCreatedAtAsc(UUID userId) {
        return jpaRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtAsc(userId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
