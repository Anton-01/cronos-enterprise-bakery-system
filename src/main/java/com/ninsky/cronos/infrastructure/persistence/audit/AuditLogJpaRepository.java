package com.ninsky.cronos.infrastructure.persistence.audit;

import com.ninsky.cronos.infrastructure.persistence.audit.entity.AuditLogJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {
    Page<AuditLogJpaEntity> findByTargetTypeAndTargetId(String targetType, String targetId, Pageable pageable);
}
