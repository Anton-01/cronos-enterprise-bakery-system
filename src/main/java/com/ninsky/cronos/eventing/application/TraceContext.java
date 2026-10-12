package com.ninsky.cronos.eventing.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a piece of work comes from: the request (or job run) that started the chain
 * ({@code correlationId}), the event being handled when new events are published ({@code causationId}),
 * the W3C trace id of the logs, and the acting user when there is one.
 *
 * @param correlationId never null
 * @param causationId   the outbox id of the event whose handler is running, null on a request thread
 * @param traceId       32 lower-case hex digits, or whatever the caller propagated in {@code X-Trace-Id}
 * @param actorId       the authenticated user, null for anonymous requests and system jobs
 * @param sessionId     the server session of the caller ({@code sid} claim), when known
 */
public record TraceContext(UUID correlationId, UUID causationId, String traceId, UUID actorId, UUID sessionId) {

    public TraceContext {
        Objects.requireNonNull(correlationId, "correlationId");
    }

    /** Same chain, now acting on behalf of {@code actor} (resolved after authentication). */
    public TraceContext withActor(UUID actor, UUID session) {
        return new TraceContext(correlationId, causationId, traceId, actor, session);
    }

    /** Context of a handler processing event {@code eventId}: new events it publishes are its effects. */
    public TraceContext causedBy(UUID eventId) {
        return new TraceContext(correlationId, eventId, traceId, actorId, sessionId);
    }
}
