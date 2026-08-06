package com.ninsky.cronos.application.service.audit;

import com.ninsky.cronos.application.response.audit.AuditLogResponse;
import com.ninsky.cronos.domain.model.audit.AuditLogEntry;
import com.ninsky.cronos.domain.port.audit.AuditLogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogPort auditLogPort;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(String targetType, String targetId, Pageable pageable) {
        Page<AuditLogEntry> entries = (targetType != null && targetId != null)
                ? auditLogPort.findByTargetTypeAndTargetId(targetType, targetId, pageable)
                : auditLogPort.findAll(pageable);

        return entries.map(this::mapToResponse);
    }

    private AuditLogResponse mapToResponse(AuditLogEntry entry) {
        return AuditLogResponse.builder().id(entry.getId()).actorUserId(entry.getActorUserId())
                .actorUsername(entry.getActorUsername()).action(entry.getAction().name())
                .targetType(entry.getTargetType()).targetId(entry.getTargetId())
                .details(entry.getDetails()).ipAddress(entry.getIpAddress())
                .userAgent(entry.getUserAgent()).createdAt(entry.getCreatedAt()).build();
    }
}
