package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.application.port.ImageProcessor;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.avatar.domain.AvatarObjectReleased;
import com.ninsky.cronos.account.avatar.domain.AvatarPolicy;
import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.ImageRejected;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.access.AccessGuards;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.user.api.AvatarChanged;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Admin avatar changes reusing the account-settings image pipeline: decode/re-encode and store outside
 * the transaction, commit the key, release the previous object after commit (spec §3.5).
 */
@Slf4j
@Service
public class UserAvatarService {

    private final ImageProcessor images;
    private final AvatarStorage storage;
    private final UserAccountRepository accounts;
    private final UserReadRepository users;
    private final AccessGuards guards;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final UserDirectory directory;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public UserAvatarService(ImageProcessor images, AvatarStorage storage, UserAccountRepository accounts,
                             UserReadRepository users, AccessGuards guards, ActorProvider actors, AuditRecorder recorder,
                             UserDirectory directory, ApplicationEventPublisher events, PlatformTransactionManager transactions) {
        this.images = images;
        this.storage = storage;
        this.accounts = accounts;
        this.users = users;
        this.guards = guards;
        this.actors = actors;
        this.recorder = recorder;
        this.directory = directory;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Validates, re-encodes and stores the image; the caller commits the returned key. */
    public AvatarKey store(UUID userId, byte[] upload, String field) {
        if (upload == null || upload.length == 0) {
            throw ApiException.invalid(field, "account.avatar.file.required");
        }
        if (upload.length > AvatarPolicy.MAX_UPLOAD_BYTES) {
            throw ApiException.of(ApiErrorCode.PAYLOAD_TOO_LARGE, field, "account.avatar.file.tooLarge",
                    AvatarPolicy.MAX_UPLOAD_BYTES / (1024 * 1024));
        }
        try {
            byte[] jpeg = images.process(upload).jpeg();
            AvatarKey key = AvatarKey.forContent(userId, jpeg);
            storage.put(key, jpeg);
            return key;
        } catch (AccountDomainException e) {
            throw translate(e, field);
        }
    }

    /** Compensation when the commit that should reference {@code key} failed. */
    public void discard(AvatarKey key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not discard avatar object {}: {}", key.fileName(), e.getMessage());
        }
    }

    public AvatarChanged replace(UUID userId, byte[] upload) {
        Actor actor = actors.require();
        UserRow row = require(userId);
        guards.requireCanModify(actor, List.of(userId));
        AvatarKey key = store(userId, upload, "file");
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                AvatarKey previous = AvatarKey.ofNullable(require(userId).avatarKey());
                var updated = accounts.replaceAvatar(userId, key);
                if (previous != null && !previous.equals(key)) {
                    events.publishEvent(new AvatarObjectReleased(userId, previous));
                }
                audit(row, previous, key);
                return new AvatarChanged(directory.avatarUrl(key.value()),
                        updated.updatedAt() == null ? null : TenantTime.toInstant(updated.updatedAt()));
            }));
        } catch (RuntimeException e) {
            if (!key.value().equals(users.find(userId).map(UserRow::avatarKey).orElse(null))) {
                discard(key);
            }
            throw e;
        }
    }

    /** Idempotent: no avatar, nothing to do. */
    public void remove(UUID userId) {
        Actor actor = actors.require();
        UserRow row = require(userId);
        guards.requireCanModify(actor, List.of(userId));
        tx.executeWithoutResult(status -> {
            AvatarKey previous = AvatarKey.ofNullable(require(userId).avatarKey());
            if (previous == null) {
                return;
            }
            accounts.replaceAvatar(userId, null);
            events.publishEvent(new AvatarObjectReleased(userId, previous));
            audit(row, previous, null);
        });
    }

    private void audit(UserRow row, AvatarKey from, AvatarKey to) {
        recorder.record(AuditEvent.of(AuditAction.USER_AVATAR_CHANGED, AuditTargets.USER, row.id(), row.displayName())
                .changes(Changes.of("avatarKey", from == null ? null : from.value(), to == null ? null : to.value()))
                .build());
    }

    private UserRow require(UUID userId) {
        return users.find(userId).orElseThrow(() -> ApiException.notFound("iam.user.notFound"));
    }

    private static ApiException translate(AccountDomainException e, String field) {
        if (e.primary() instanceof ImageRejected rejected) {
            ApiErrorCode code = switch (rejected.reason()) {
                case TOO_LARGE -> ApiErrorCode.PAYLOAD_TOO_LARGE;
                case UNSUPPORTED_TYPE -> ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
                case INVALID -> ApiErrorCode.VALIDATION_ERROR;
            };
            return ApiException.of(code, field, rejected.messageKey(), rejected.args().toArray());
        }
        throw e;
    }
}
