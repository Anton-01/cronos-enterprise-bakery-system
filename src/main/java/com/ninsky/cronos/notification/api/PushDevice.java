package com.ninsky.cronos.notification.api;

import java.time.Instant;
import java.util.UUID;

/** A browser subscribed to the caller's pushes (the endpoint itself is a capability and is not returned). */
public record PushDevice(UUID id, String deviceLabel, String browser, String os, String locale, Instant createdAt, Instant lastSuccessAt,
                         int failureCount) {
}
