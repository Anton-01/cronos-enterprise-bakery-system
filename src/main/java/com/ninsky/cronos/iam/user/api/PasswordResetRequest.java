package com.ninsky.cronos.iam.user.api;

/** {@code POST /iam/users/{id}/password-reset}. */
public record PasswordResetRequest(Mode mode, Boolean revokeSessions) {
    public enum Mode {
        EMAIL_LINK,
        TEMPORARY_PASSWORD
    }
}
