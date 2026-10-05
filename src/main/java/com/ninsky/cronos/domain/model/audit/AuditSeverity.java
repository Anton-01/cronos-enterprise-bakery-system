package com.ninsky.cronos.domain.model.audit;

/** Ordered from least to most severe. */
public enum AuditSeverity {
    INFO,
    NOTICE,
    WARNING,
    CRITICAL;

    public AuditSeverity atLeast(AuditSeverity other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
