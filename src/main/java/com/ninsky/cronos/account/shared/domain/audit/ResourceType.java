package com.ninsky.cronos.account.shared.domain.audit;

/** What an {@link AuditChange} is about; stored as {@code audit_log.target_type}. */
public enum ResourceType {
    USER_PROFILE,
    USER_AVATAR,
    FISCAL_DATA,
    CREDENTIALS
}
