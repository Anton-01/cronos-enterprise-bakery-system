package com.ninsky.cronos.iam.twofactor.api;

import java.time.Instant;
import java.util.UUID;

/** Contract §8.2 {@code TwoFactorEnrollment}; carries the secret, so it is never logged. */
public record TwoFactorEnrollment(UUID enrollmentId, String secret, String otpauthUri, String qrCodeDataUri, String issuer,
                                  String accountName, int digits, int periodSeconds, Instant expiresAt) {

    @Override
    public String toString() {
        return "TwoFactorEnrollment[enrollmentId=" + enrollmentId + ", expiresAt=" + expiresAt + "]";
    }
}
