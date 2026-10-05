package com.ninsky.cronos.iam.audit.query;

import com.ninsky.cronos.iam.shared.UserRef;

import java.time.Instant;
import java.util.Map;

/** {@code AuditEvent} of the contract (spec §7.1); ids are strings, times UTC instants. */
public record AuditEventView(
        String id,
        Instant occurredAt,
        String category,
        String action,
        String outcome,
        String severity,
        UserRef actor,
        Target target,
        String summary,
        String reason,
        Map<String, Object> changes,
        String ipAddress,
        String userAgent,
        String traceId
) {
    public record Target(String type, String id, String label) {
    }
}
