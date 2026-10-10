package com.ninsky.cronos.eventing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ninsky.cronos.iam.shared.UserRef;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One event of the operational trail. {@code payload} is redacted ({@code @Sensitive} components removed) and only
 * filled by the trail endpoint.
 */
public record EventTrailEntry(UUID id, String type, String aggregateType, UUID aggregateId, Instant occurredAt,
                              String status, int attempts, String lastError, UUID correlationId, UUID causationId,
                              String traceId, UserRef actor, List<HandlerRun> handlers, JsonNode payload) {

    public EventTrailEntry {
        handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    /** A handler that applied the event. */
    public record HandlerRun(String name, Instant processedAt) {
    }
}
