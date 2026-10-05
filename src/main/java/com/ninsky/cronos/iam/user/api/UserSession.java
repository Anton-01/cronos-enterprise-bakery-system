package com.ninsky.cronos.iam.user.api;

import java.time.Instant;
import java.util.UUID;

/** Live session of a user. */
public record UserSession(UUID id, String ipAddress, String browser, String os, String device, String location,
                          Instant createdAt, Instant lastActivityAt, Instant expiresAt) {
}
