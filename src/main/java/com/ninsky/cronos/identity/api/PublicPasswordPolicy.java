package com.ninsky.cronos.identity.api;

/** {@code GET /auth/password-policy}: what the registration and reset forms check while the user types. */
public record PublicPasswordPolicy(int minLength, boolean requireUppercase, boolean requireLowercase, boolean requireDigit,
                                   boolean requireSymbol, boolean disallowUserInfo) {
}
