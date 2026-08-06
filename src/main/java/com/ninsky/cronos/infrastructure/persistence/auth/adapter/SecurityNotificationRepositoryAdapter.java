package com.ninsky.cronos.infrastructure.persistence.auth.adapter;

import com.ninsky.cronos.domain.model.auth.SecurityNotification;
import com.ninsky.cronos.domain.port.auth.SecurityNotificationRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.auth.SecurityNotificationJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.mapper.SecurityNotificationMapper;
import org.springframework.stereotype.Component;

@Component
public class SecurityNotificationRepositoryAdapter implements SecurityNotificationRepositoryPort {

    private final SecurityNotificationJpaRepository jpaRepository;
    private final SecurityNotificationMapper mapper;

    public SecurityNotificationRepositoryAdapter(SecurityNotificationJpaRepository jpaRepository, SecurityNotificationMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public SecurityNotification save(SecurityNotification notification) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(notification)));
    }
}
