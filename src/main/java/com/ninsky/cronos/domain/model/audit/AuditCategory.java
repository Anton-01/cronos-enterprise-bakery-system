package com.ninsky.cronos.domain.model.audit;

/** Audit event families (spec §7.1). */
public enum AuditCategory {
    AUTHENTICATION,
    USER_ADMINISTRATION,
    ACCESS_CONTROL,
    SECURITY,
    CONFIGURATION,
    DATA
}
