package com.ninsky.cronos.eventing.application;

import com.ninsky.cronos.eventing.domain.DomainEvent;

/**
 * Consumer of one event class, invoked by the outbox relay after the publishing transaction committed.
 * Each invocation runs in its own transaction together with the {@code processed_events} row of this handler,
 * so a handler applies an event at most once even though delivery is at-least-once. Throwing schedules a retry
 * of the whole message; handlers that already succeeded for it are skipped on the retry.
 *
 * @param <E> the handled event class
 */
public interface OutboxHandler<E extends DomainEvent> {

    /** The event class this handler consumes. */
    Class<E> eventClass();

    /** Applies {@code event}; must not swallow failures that deserve a retry. */
    void handle(E event, EventEnvelope envelope);

    /** Stable name stored in {@code processed_events}; renaming a handler re-applies old events, so keep it. */
    default String name() {
        String simpleName = getClass().getSimpleName();
        int proxyMarker = simpleName.indexOf("$$");
        return proxyMarker < 0 ? simpleName : simpleName.substring(0, proxyMarker);
    }
}
