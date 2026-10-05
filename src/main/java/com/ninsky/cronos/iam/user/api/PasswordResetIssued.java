package com.ninsky.cronos.iam.user.api;

import java.time.Instant;

/** Where the reset went; {@code expiresAt} is null for temporary passwords. */
public record PasswordResetIssued(PasswordResetRequest.Mode mode, String deliveredTo, Instant expiresAt) {
}
