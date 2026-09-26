package com.ninsky.cronos.account.profile.application;

import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.profile.domain.UsernameChanged;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter.RateLimitedAction;
import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.DuplicateUsername;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.VersionMismatch;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.WriteRateLimited;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.PiiMasker;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.account.shared.domain.audit.AuditDiff;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** PUT /users/me — full replace of the user-editable profile fields. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateMyProfileUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final AccountRateLimiter rateLimiter;
    private final AuditTrail auditTrail;
    private final PiiMasker piiMasker;
    private final ApplicationEventPublisher eventPublisher;

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public UserAccount execute(ProfileUpdate update) {
        UUID userId = currentUserProvider.currentUserId();

        var decision = rateLimiter.tryConsume(userId, RateLimitedAction.PROFILE_WRITE);
        if (!decision.allowed()) {
            throw new AccountDomainException(WriteRateLimited.of(decision.retryAfterSeconds()));
        }

        UserAccount current = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!update.expectedVersion().matches(current.version())) {
            throw new AccountDomainException(VersionMismatch.of());
        }
        if (userAccountRepository.isUsernameTakenByOther(update.username(), userId)) {
            throw new AccountDomainException(DuplicateUsername.of(update.username()));
        }

        UserAccount updated = userAccountRepository.applyProfile(userId, update);

        var diff = AuditDiff.between(current.editableSnapshot(), updated.editableSnapshot(), piiMasker);
        if (!diff.isEmpty()) {
            auditTrail.record(new AuditChange.ProfileChanged(userId, userId, diff));
        }
        if (!current.username().equals(updated.username())) {
            eventPublisher.publishEvent(new UsernameChanged(userId, current.username(), updated.username()));
        }
        log.info("Profile updated for user {} (fields changed: {})", userId, diff.keySet());
        return updated;
    }
}
