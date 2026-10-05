package com.ninsky.cronos.iam.audit.query;

import java.time.LocalDateTime;
import java.util.UUID;

/** One stored {@code audit_log} row as read for display; {@code createdAt} is tenant wall time. */
public record AuditRow(
        long id,
        LocalDateTime createdAt,
        String category,
        String action,
        String outcome,
        String severity,
        UUID actorId,
        String actorUsername,
        String actorLabel,
        String actorAvatarKey,
        String targetType,
        String targetId,
        String targetLabel,
        String paramsJson,
        String changesJson,
        String reason,
        String ipAddress,
        String userAgent,
        String traceId
) {
}
