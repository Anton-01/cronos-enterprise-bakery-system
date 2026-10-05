package com.ninsky.cronos.iam.user;

/** Why an account changed status. */
public enum StatusReason {
    SECURITY_INCIDENT,
    POLICY_VIOLATION,
    OFFBOARDING,
    LEAVE_OF_ABSENCE,
    ROLE_CHANGE,
    ADMIN_REQUEST,
    OTHER
}
