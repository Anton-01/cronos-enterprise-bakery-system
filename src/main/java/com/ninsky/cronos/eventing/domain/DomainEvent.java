package com.ninsky.cronos.eventing.domain;

import java.util.UUID;

/**
 * Something that happened to an aggregate. Implementations are immutable records annotated with
 * {@link EventType}; trace metadata (ids, actor, time) is stamped by the publisher, never by callers.
 * Payloads carry ids and the minimum facts consumers need, never secrets outside {@link Sensitive} components.
 */
public interface DomainEvent {

    /** Upper-case aggregate kind, e.g. {@code QUOTE}; with {@link #aggregateId()} it orders dispatch. */
    String aggregateType();

    /** Identity of the aggregate the event belongs to; events of one aggregate are dispatched in order. */
    UUID aggregateId();
}
