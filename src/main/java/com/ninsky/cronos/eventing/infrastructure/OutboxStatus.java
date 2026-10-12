package com.ninsky.cronos.eventing.infrastructure;

/** Life of an outbox message. */
public enum OutboxStatus {
    /** Waiting for (another) dispatch attempt at {@code available_at}. */
    PENDING,
    /** Every handler applied it. */
    DISPATCHED,
    /** Reserved for messages an operator abandoned on purpose. */
    FAILED,
    /** Retries exhausted; kept until an administrator re-queues it. */
    DEAD
}
