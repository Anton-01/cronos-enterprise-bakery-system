package com.ninsky.cronos.eventing.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** One {@code outbox_messages} row; {@code payload} is the stored JSON (sensitive components sealed). */
public record OutboxMessage(UUID id, String type, String aggregateType, UUID aggregateId, String payload,
                            UUID correlationId, UUID causationId, String traceId, UUID actorId,
                            Instant occurredAt, Instant availableAt, OutboxStatus status, int attempts,
                            String lastError, Instant dispatchedAt, String dedupeKey) {
}
