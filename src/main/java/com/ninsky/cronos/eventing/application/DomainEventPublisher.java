package com.ninsky.cronos.eventing.application;

import com.ninsky.cronos.eventing.domain.DomainEvent;

/**
 * Publishes domain events into the transactional outbox of the current transaction. Nothing leaves the
 * process before the transaction commits, and nothing is published if it rolls back.
 */
public interface DomainEventPublisher {

    /**
     * Publishes {@code event} with no delivery hints.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException when no transaction is active
     */
    default void publish(DomainEvent event) {
        publish(event, PublishOptions.NONE);
    }

    /**
     * Publishes {@code event}; returns false when {@code options.dedupeKey()} coalesced it into an earlier one.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException when no transaction is active
     */
    boolean publish(DomainEvent event, PublishOptions options);
}
