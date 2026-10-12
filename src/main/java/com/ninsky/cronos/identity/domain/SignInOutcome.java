package com.ninsky.cronos.identity.domain;

/** Stored outcome of one sign-in attempt ({@code login_history.outcome}, §2.3.3). */
public enum SignInOutcome {
    SUCCESS,
    INVALID_CREDENTIALS,
    LOCKED,
    RATE_LIMITED,
    PENDING_APPROVAL,
    EMAIL_NOT_VERIFIED,
    REJECTED,
    DISABLED,
    TWO_FACTOR_REQUIRED,
    TWO_FACTOR_FAILED;

    /** True for the outcome of a completed sign-in. */
    public boolean successful() {
        return this == SUCCESS;
    }
}
