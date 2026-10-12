package com.ninsky.cronos.infrastructure.audit;

import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.domain.TimeOrderedIds;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * Queues audit rows in the outbox: inside the caller's transaction when there is one (the row exists only if the
 * change commits), else in a transaction of its own.
 */
@Component
public class AuditEntries {

    private final DomainEventPublisher publisher;
    private final TransactionTemplate standalone;
    private final Clock clock;

    public AuditEntries(DomainEventPublisher publisher, PlatformTransactionManager transactions, Clock clock) {
        this.publisher = publisher;
        this.standalone = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** A fresh time-ordered id for an entry. */
    public UUID nextId() {
        return TimeOrderedIds.next(clock);
    }

    public void publish(AuditEntryRecorded entry) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            publisher.publish(entry);
        } else {
            standalone.executeWithoutResult(status -> publisher.publish(entry));
        }
    }
}
