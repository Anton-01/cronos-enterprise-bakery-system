package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.avatar.domain.AvatarObjectReleased;
import com.ninsky.cronos.account.avatar.domain.AvatarReleased;
import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes avatar objects their user no longer references. The release enters the outbox in the transaction that
 * replaced the avatar (nothing is deleted when it rolls back) and the object is deleted after commit, retried while the
 * storage is unavailable instead of being left as an orphan. An object that became current again is kept.
 */
@Slf4j
@Component
public class AvatarObjectCleanupListener implements OutboxHandler<AvatarReleased> {

    private final AvatarStorage avatarStorage;
    private final UserAccountRepository userAccountRepository;
    private final DomainEventPublisher publisher;
    private final TransactionTemplate standalone;

    public AvatarObjectCleanupListener(AvatarStorage avatarStorage, UserAccountRepository userAccountRepository,
                                       DomainEventPublisher publisher, PlatformTransactionManager transactions) {
        this.avatarStorage = avatarStorage;
        this.userAccountRepository = userAccountRepository;
        this.publisher = publisher;
        this.standalone = new TransactionTemplate(transactions);
    }

    /** Queues the deletion with the caller's transaction (or in one of its own outside a transaction). */
    @EventListener
    public void on(AvatarObjectReleased event) {
        AvatarReleased released = new AvatarReleased(event.userId(), event.key().value());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            publisher.publish(released);
        } else {
            standalone.executeWithoutResult(status -> publisher.publish(released));
        }
    }

    @Override
    public Class<AvatarReleased> eventClass() {
        return AvatarReleased.class;
    }

    @Override
    public void handle(AvatarReleased event, EventEnvelope envelope) {
        AvatarKey key = new AvatarKey(event.key());
        boolean stillReferenced = userAccountRepository.findById(event.userId())
                .map(UserAccount::avatarKey)
                .filter(key::equals)
                .isPresent();
        if (stillReferenced) {
            return;
        }
        avatarStorage.delete(key);
        log.debug("Deleted released avatar object {} of user {}", key.fileName(), event.userId());
    }

    @Override
    public String name() {
        return "AvatarObjectCleanup";
    }
}
