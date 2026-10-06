package com.ninsky.cronos.iam.twofactor.api;

import java.time.Instant;
import java.util.List;

/** Contract §8.2 {@code TwoFactorStatus}. */
public record TwoFactorStatus(boolean enabled, boolean required, List<String> requiredBy, String method, Instant enrolledAt,
                              int recoveryCodesRemaining) {

    public static final String TOTP = "TOTP";
}
