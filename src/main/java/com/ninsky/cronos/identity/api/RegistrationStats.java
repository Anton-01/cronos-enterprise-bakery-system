package com.ninsky.cronos.identity.api;

import java.time.Instant;

/** Review queue figures (§3.1); {@code medianReviewHours} over decisions of the last 90 days, null without any. */
public record RegistrationStats(long pending, long pendingUnverified, long approvedLast30Days, long rejectedLast30Days,
                                Instant oldestPendingAt, Double medianReviewHours) {
}
