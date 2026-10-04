package com.ninsky.cronos.domain.model.audit;

public enum AuditAction {
    USER_CREATED,
    USER_CREATED_WITH_PROFILE,
    USER_UPDATED,
    USER_LOCKED,
    USER_UNLOCKED,
    USER_ROLES_ASSIGNED,
    USER_FORCE_LOGOUT,
    USER_TWO_FACTOR_DISABLED,
    USER_PASSWORD_RESET_INITIATED,
    ROLE_CREATED,
    ROLE_UPDATED,
    // Account settings (self-service, actor == target)
    PROFILE_CHANGED,
    AVATAR_CHANGED,
    AVATAR_REMOVED,
    FISCAL_DATA_CREATED,
    FISCAL_DATA_UPDATED,
    PASSWORD_CHANGED,
    // Unit catalog (system-wide master data; every change restates conversions for all users)
    UNIT_TYPE_CREATED,
    UNIT_TYPE_UPDATED,
    UNIT_TYPE_STATUS_CHANGED,
    UNIT_TYPE_DELETED,
    MEASUREMENT_UNIT_CREATED,
    MEASUREMENT_UNIT_UPDATED,
    MEASUREMENT_UNIT_STATUS_CHANGED,
    MEASUREMENT_UNIT_DELETED,
    // Bulk imports: one entry per batch outcome; row-level detail lives in data_import_batches
    DATA_IMPORT_COMMITTED,
    DATA_IMPORT_REJECTED,
    DATA_IMPORT_FAILED
}
