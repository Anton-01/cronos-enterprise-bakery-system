package com.ninsky.cronos.infrastructure.persistence.audit.mapper;

import com.ninsky.cronos.domain.model.audit.AuditLogEntry;
import com.ninsky.cronos.infrastructure.persistence.audit.entity.AuditLogJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogEntry toDomain(AuditLogJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return AuditLogEntry.builder()
                .id(entity.getId())
                .actorUserId(entity.getActorUserId())
                .actorUsername(entity.getActorUsername())
                .action(entity.getAction())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .details(entity.getDetails())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public AuditLogJpaEntity toEntity(AuditLogEntry domain) {
        if (domain == null) {
            return null;
        }
        return AuditLogJpaEntity.builder()
                .id(domain.getId())
                .actorUserId(domain.getActorUserId())
                .actorUsername(domain.getActorUsername())
                .action(domain.getAction())
                .targetType(domain.getTargetType())
                .targetId(domain.getTargetId())
                .details(domain.getDetails())
                .ipAddress(domain.getIpAddress())
                .userAgent(domain.getUserAgent())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
