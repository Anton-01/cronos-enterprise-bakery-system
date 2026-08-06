package com.ninsky.cronos.infrastructure.persistence.audit.adapter;

import com.ninsky.cronos.domain.model.audit.AuditLogEntry;
import com.ninsky.cronos.domain.port.audit.AuditLogPort;
import com.ninsky.cronos.infrastructure.persistence.audit.AuditLogJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.audit.mapper.AuditLogMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class AuditLogRepositoryAdapter implements AuditLogPort {

    private final AuditLogJpaRepository jpaRepository;
    private final AuditLogMapper mapper;

    public AuditLogRepositoryAdapter(AuditLogJpaRepository jpaRepository, AuditLogMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(entry)));
    }

    @Override
    public Page<AuditLogEntry> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<AuditLogEntry> findByTargetTypeAndTargetId(String targetType, String targetId, Pageable pageable) {
        return jpaRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable).map(mapper::toDomain);
    }
}
