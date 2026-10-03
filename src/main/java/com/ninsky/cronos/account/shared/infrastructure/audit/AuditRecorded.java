package com.ninsky.cronos.account.shared.infrastructure.audit;

import com.ninsky.cronos.account.shared.domain.audit.AuditChange;

import java.time.LocalDateTime;

/**
 * An {@link AuditChange} plus the request context captured on the request thread — the async,
 * after-commit writer runs on a pool thread where the servlet request is no longer available.
 */
public record AuditRecorded(AuditChange change, LocalDateTime occurredAt, String ipAddress, String userAgent, String traceId) {
}
