package com.ninsky.cronos.identity.api;

import java.time.Instant;

/** Answer of {@code POST /auth/register}; identical in shape whether or not the email already had an account. */
public record RegistrationReceipt(String reference, String email, String status, boolean emailVerificationRequired,
                                  Instant submittedAt) {
}
