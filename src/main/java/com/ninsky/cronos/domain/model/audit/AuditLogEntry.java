package com.ninsky.cronos.domain.model.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {
    private Long id;
    private UUID actorUserId;
    private String actorUsername;
    private AuditAction action;
    private String targetType;
    private String targetId;
    private String details;
    private String ipAddress;
    private String userAgent;
    private String traceId;
    /** Field-level diff as JSON ({@code {"field": {"from": .., "to": ..}}}), PII already masked. */
    private String changes;
    private LocalDateTime createdAt;
}
