package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.shared.AccountCreated;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes {@code identity.account-created} in the transaction that created the account, so other modules prepare
 * their defaults after commit instead of inside the request (the kitchen seeding used to run synchronously here).
 */
@Component
public class AccountCreatedRelay {

    private final DomainEventPublisher publisher;
    private final TransactionTemplate standalone;

    public AccountCreatedRelay(DomainEventPublisher publisher, PlatformTransactionManager transactions) {
        this.publisher = publisher;
        this.standalone = new TransactionTemplate(transactions);
    }

    @EventListener
    public void on(AccountCreated event) {
        IdentityEvents.AccountCreated created = new IdentityEvents.AccountCreated(event.userId());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            publisher.publish(created);
        } else {
            standalone.executeWithoutResult(status -> publisher.publish(created));
        }
    }
}
