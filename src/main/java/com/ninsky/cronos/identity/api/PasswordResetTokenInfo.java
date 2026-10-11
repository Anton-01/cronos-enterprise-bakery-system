package com.ninsky.cronos.identity.api;

import java.time.Instant;

/** {@code GET /auth/password/reset/{token}}: an invalid, used or expired link is {@code valid = false} (200). */
public record PasswordResetTokenInfo(boolean valid, String maskedEmail, Instant expiresAt) {
}
