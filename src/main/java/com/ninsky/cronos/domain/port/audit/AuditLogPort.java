package com.ninsky.cronos.domain.port.audit;

import com.ninsky.cronos.domain.model.audit.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogPort {
    AuditLogEntry save(AuditLogEntry entry);
    Page<AuditLogEntry> findAll(Pageable pageable);
    Page<AuditLogEntry> findByTargetTypeAndTargetId(String targetType, String targetId, Pageable pageable);
}
