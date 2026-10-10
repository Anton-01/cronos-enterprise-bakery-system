package com.ninsky.cronos.eventing.api;

import java.time.Instant;
import java.util.UUID;

/** Search criteria of {@code GET /admin/events}; every field optional. */
public record EventTrailFilter(String aggregateType, UUID aggregateId, UUID correlationId, String type, String status,
                               Instant from, Instant to) {
}
