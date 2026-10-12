package com.ninsky.cronos.identity.domain;

/** Why a registration was rejected; {@code OTHER} requires a comment. */
public enum RejectReason {
    UNKNOWN_APPLICANT,
    DUPLICATE_ACCOUNT,
    INCOMPLETE_INFORMATION,
    POLICY_VIOLATION,
    OTHER
}
