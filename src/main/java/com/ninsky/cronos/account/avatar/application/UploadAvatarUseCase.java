package com.ninsky.cronos.account.avatar.application;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.application.port.ImageProcessor;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.avatar.domain.AvatarObjectReleased;
import com.ninsky.cronos.account.avatar.domain.AvatarPolicy;
import com.ninsky.cronos.account.avatar.domain.ProcessedImage;
import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter.RateLimitedAction;
import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.ImageRejected;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.UploadRateLimited;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.VersionMismatch;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.account.shared.domain.audit.FieldDiff;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * PUT /users/me/avatar. Ordering is deliberate:
 * <ol>
 *   <li>rate-limit before any CPU is spent on decoding;</li>
 *   <li>decode / re-encode and write the new object OUTSIDE the DB transaction (no connection held
 *       during image work or storage I/O);</li>
 *   <li>commit the new key; the previous object is deleted only AFTER_COMMIT
 *       ({@code AvatarObjectCleanupListener}) so a rollback never points the user at a deleted object;</li>
 *   <li>if the commit fails, delete the new object again unless the committed row references it.</li>
 * </ol>
 */
@Slf4j
@Service
public class UploadAvatarUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final ImageProcessor imageProcessor;
    private final AvatarStorage avatarStorage;
    private final AccountRateLimiter rateLimiter;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public UploadAvatarUseCase(CurrentUserProvider currentUserProvider, UserAccountRepository userAccountRepository,
                               ImageProcessor imageProcessor, AvatarStorage avatarStorage, AccountRateLimiter rateLimiter,
                               AuditTrail auditTrail, ApplicationEventPublisher eventPublisher,
                               PlatformTransactionManager transactionManager) {
        this.currentUserProvider = currentUserProvider;
        this.userAccountRepository = userAccountRepository;
        this.imageProcessor = imageProcessor;
        this.avatarStorage = avatarStorage;
        this.rateLimiter = rateLimiter;
        this.auditTrail = auditTrail;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PreAuthorize("isAuthenticated()")
    public UserAccount execute(byte[] upload, ExpectedVersion expectedVersion) {
        UUID userId = currentUserProvider.currentUserId();

        var decision = rateLimiter.tryConsume(userId, RateLimitedAction.AVATAR_UPLOAD);
        if (!decision.allowed()) {
            throw new AccountDomainException(UploadRateLimited.of(decision.retryAfterSeconds()));
        }
        if (upload == null || upload.length == 0) {
            throw new AccountDomainException(ImageRejected.of(ImageRejected.Reason.INVALID, "account.avatar.file.required"));
        }
        if (upload.length > AvatarPolicy.MAX_UPLOAD_BYTES) {
            throw new AccountDomainException(ImageRejected.of(ImageRejected.Reason.TOO_LARGE, "account.avatar.file.tooLarge",
                    AvatarPolicy.MAX_UPLOAD_BYTES / (1024 * 1024)));
        }

        ProcessedImage image = imageProcessor.process(upload);
        AvatarKey newKey = AvatarKey.forContent(userId, image.jpeg());
        avatarStorage.put(newKey, image.jpeg());

        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> commit(userId, newKey, expectedVersion)));
        } catch (RuntimeException e) {
            discardIfUnreferenced(userId, newKey);
            throw e;
        }
    }

    private UserAccount commit(UUID userId, AvatarKey newKey, ExpectedVersion expectedVersion) {
        UserAccount current = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!expectedVersion.matches(current.version())) {
            throw new AccountDomainException(VersionMismatch.of());
        }

        AvatarKey previousKey = current.avatarKey();
        UserAccount updated = userAccountRepository.replaceAvatar(userId, newKey);

        if (previousKey != null && !previousKey.equals(newKey)) {
            eventPublisher.publishEvent(new AvatarObjectReleased(userId, previousKey));
        }
        var changes = new LinkedHashMap<String, FieldDiff>();
        changes.put("avatarKey", new FieldDiff(previousKey == null ? null : previousKey.value(), newKey.value()));
        auditTrail.record(new AuditChange.AvatarChanged(userId, userId, changes));

        log.info("Avatar updated for user {} -> {}", userId, newKey.fileName());
        return updated;
    }

    /** Compensation for a failed commit; safe against a concurrent identical upload that did commit. */
    private void discardIfUnreferenced(UUID userId, AvatarKey newKey) {
        try {
            boolean referenced = userAccountRepository.findById(userId)
                    .map(UserAccount::avatarKey)
                    .filter(newKey::equals)
                    .isPresent();
            if (!referenced) {
                avatarStorage.delete(newKey);
            }
        } catch (RuntimeException cleanupFailure) {
            log.warn("Could not discard uncommitted avatar object {}: {}", newKey.fileName(), cleanupFailure.getMessage());
        }
    }
}
