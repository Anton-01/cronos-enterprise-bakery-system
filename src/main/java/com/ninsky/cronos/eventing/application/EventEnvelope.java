package com.ninsky.cronos.eventing.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox metadata of the event a handler is processing.
 *
 * @param attempts deliveries already tried before this one (0 on the first attempt)
 */
public record EventEnvelope(UUID id, String type, String aggregateType, UUID aggregateId, UUID correlationId,
                            UUID causationId, String traceId, UUID actorId, Instant occurredAt, int attempts) {

    /** The trace context handlers run with: same chain, caused by this event. */
    public TraceContext traceContext() {
        return new TraceContext(correlationId, id, traceId, actorId, null);
    }
}
