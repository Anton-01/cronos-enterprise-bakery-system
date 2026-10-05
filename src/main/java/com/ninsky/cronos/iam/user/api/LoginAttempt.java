package com.ninsky.cronos.iam.user.api;

import java.time.Instant;
import java.util.UUID;

/** One sign-in attempt; {@code failureReason} is localised and generic. */
public record LoginAttempt(UUID id, Instant occurredAt, String outcome, String failureReason, String ipAddress,
                           String browser, String os, String location) {
}
