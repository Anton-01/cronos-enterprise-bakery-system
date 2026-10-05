package com.ninsky.cronos.iam.token;

/** What a single-use token in {@code user_tokens} is for. */
public enum TokenPurpose {
    INVITATION,
    PASSWORD_RESET,
    EMAIL_VERIFICATION
}
