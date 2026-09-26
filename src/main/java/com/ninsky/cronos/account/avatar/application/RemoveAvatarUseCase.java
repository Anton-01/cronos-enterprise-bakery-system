package com.ninsky.cronos.account.avatar.application;

import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.avatar.domain.AvatarObjectReleased;
import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.account.shared.domain.audit.FieldDiff;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.UUID;

/** DELETE /users/me/avatar — idempotent: removing an absent avatar succeeds and changes nothing. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoveAvatarUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void execute() {
        UUID userId = currentUserProvider.currentUserId();
        UserAccount current = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        AvatarKey previousKey = current.avatarKey();
        if (previousKey == null) {
            return;
        }

        userAccountRepository.replaceAvatar(userId, null);
        eventPublisher.publishEvent(new AvatarObjectReleased(userId, previousKey));

        var changes = new LinkedHashMap<String, FieldDiff>();
        changes.put("avatarKey", new FieldDiff(previousKey.value(), null));
        auditTrail.record(new AuditChange.AvatarRemoved(userId, userId, changes));
        log.info("Avatar removed for user {}", userId);
    }
}
