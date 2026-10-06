package com.ninsky.cronos.iam.policy;

import java.util.UUID;

/** A user's 2FA was enabled, disabled or reset; evicts the gate cache after commit. */
public record TwoFactorChanged(UUID userId) {
}
