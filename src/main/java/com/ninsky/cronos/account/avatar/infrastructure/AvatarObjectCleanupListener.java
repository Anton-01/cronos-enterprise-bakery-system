package com.ninsky.cronos.account.avatar.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarObjectReleased;
import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes a replaced/removed avatar object only after the DB change committed. Idempotent and
 * safe to re-run: deleting a missing object is a no-op, and an object the user references again
 * (re-upload of the identical image → same content-addressed key) is kept. A storage failure is
 * logged and leaves an orphan — never an error for a request whose change already committed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarObjectCleanupListener {

    private final AvatarStorage avatarStorage;
    private final UserAccountRepository userAccountRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(AvatarObjectReleased event) {
        try {
            boolean stillReferenced = userAccountRepository.findById(event.userId())
                    .map(UserAccount::avatarKey)
                    .filter(event.key()::equals)
                    .isPresent();
            if (stillReferenced) {
                return;
            }
            avatarStorage.delete(event.key());
            log.debug("Deleted released avatar object {} of user {}", event.key().fileName(), event.userId());
        } catch (RuntimeException e) {
            log.warn("Could not delete released avatar object {} of user {} (left as orphan): {}",
                    event.key().fileName(), event.userId(), e.getMessage());
        }
    }
}
