package com.ninsky.cronos.account.avatar.application;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.application.port.ImageProcessor;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.avatar.domain.AvatarObjectReleased;
import com.ninsky.cronos.account.avatar.domain.AvatarPolicy;
import com.ninsky.cronos.account.avatar.domain.DetectedImageType;
import com.ninsky.cronos.account.avatar.domain.ImageDimensions;
import com.ninsky.cronos.account.avatar.domain.ProcessedImage;
import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter.Decision;
import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadAvatarUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final ImageProcessor imageProcessor = mock(ImageProcessor.class);
    private final AvatarStorage storage = mock(AvatarStorage.class);
    private final AccountRateLimiter rateLimiter = mock(AccountRateLimiter.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UploadAvatarUseCase useCase = new UploadAvatarUseCase(() -> USER_ID, repository, imageProcessor, storage,
            rateLimiter, auditTrail, events, mock(PlatformTransactionManager.class));

    private final AvatarKey previousKey = AvatarKey.forContent(USER_ID, new byte[]{9});
    private final AvatarKey newKey = AvatarKey.forContent(USER_ID, JPEG);

    @BeforeEach
    void setUp() {
        when(rateLimiter.tryConsume(any(), any())).thenReturn(Decision.allow());
        when(imageProcessor.process(any())).thenReturn(new ProcessedImage(JPEG, new ImageDimensions(512, 512), new DetectedImageType.Jpeg()));
        when(repository.findById(USER_ID)).thenReturn(Optional.of(account(previousKey, 2)));
        when(repository.replaceAvatar(USER_ID, newKey)).thenReturn(account(newKey, 3));
    }

    @Test
    void storesUnderContentAddressedKeyAndReleasesThePreviousObjectOnlyViaAfterCommitEvent() {
        UserAccount updated = useCase.execute(new byte[]{1}, ExpectedVersion.ANY);

        assertThat(updated.avatarKey()).isEqualTo(newKey);
        verify(storage).put(newKey, JPEG);
        verify(events).publishEvent(new AvatarObjectReleased(USER_ID, previousKey));
        verify(storage, never()).delete(any());
    }

    @Test
    void rateLimitedUploadsNeverReachTheDecoder() {
        when(rateLimiter.tryConsume(any(), any())).thenReturn(Decision.deny(600));

        var thrown = catchThrowableOfType(AccountDomainException.class, () -> useCase.execute(new byte[]{1}, ExpectedVersion.ANY));

        assertThat(thrown.primary()).isInstanceOf(AccountDomainError.UploadRateLimited.class);
        verifyNoInteractions(imageProcessor, storage);
    }

    @Test
    void oversizedUploadIs413() {
        var thrown = catchThrowableOfType(AccountDomainException.class,
                () -> useCase.execute(new byte[(int) AvatarPolicy.MAX_UPLOAD_BYTES + 1], ExpectedVersion.ANY));

        assertThat(thrown.primary()).isInstanceOfSatisfying(AccountDomainError.ImageRejected.class,
                e -> assertThat(e.reason()).isEqualTo(AccountDomainError.ImageRejected.Reason.TOO_LARGE));
        verifyNoInteractions(imageProcessor);
    }

    @Test
    void failedCommitDiscardsTheNewObject() {
        var thrown = catchThrowableOfType(AccountDomainException.class, () -> useCase.execute(new byte[]{1}, ExpectedVersion.of(99)));

        assertThat(thrown.primary()).isInstanceOf(AccountDomainError.VersionMismatch.class);
        verify(storage).put(newKey, JPEG);
        verify(storage).delete(newKey);
    }

    private static UserAccount account(AvatarKey key, long version) {
        return new UserAccount(USER_ID, "admin_cronos", "admin@cronos.com", null, null, null, key, true, true, false, 0,
                null, null, null, Set.of(), LocalDateTime.now(), LocalDateTime.now(), version);
    }
}
