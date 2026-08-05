package com.ninsky.cronos.infrastructure.persistence.auth.mapper;

import com.ninsky.cronos.domain.model.auth.PasswordHistory;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.PasswordHistoryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PasswordHistoryMapper {

    public PasswordHistory toDomain(PasswordHistoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return PasswordHistory.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .passwordHash(entity.getPasswordHash())
                .changedAt(entity.getChangedAt())
                .build();
    }

    public PasswordHistoryJpaEntity toEntity(PasswordHistory domain) {
        if (domain == null) {
            return null;
        }
        return PasswordHistoryJpaEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .passwordHash(domain.getPasswordHash())
                .changedAt(domain.getChangedAt())
                .build();
    }
}
