package com.ninsky.cronos.application.response.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private UUID actorUserId;
    private String actorUsername;
    private String action;
    private String targetType;
    private String targetId;
    private String details;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
