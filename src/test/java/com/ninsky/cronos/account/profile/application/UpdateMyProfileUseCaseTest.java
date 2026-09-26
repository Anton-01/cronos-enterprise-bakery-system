package com.ninsky.cronos.account.profile.application;

import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.E164Phone;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.profile.domain.UsernameChanged;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter.Decision;
import com.ninsky.cronos.account.shared.application.port.AccountRateLimiter.RateLimitedAction;
import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import com.ninsky.cronos.account.shared.domain.PiiMasker;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.account.shared.domain.audit.FieldDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateMyProfileUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final CurrentUserProvider currentUser = () -> USER_ID;
    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final AccountRateLimiter rateLimiter = mock(AccountRateLimiter.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UpdateMyProfileUseCase useCase =
            new UpdateMyProfileUseCase(currentUser, repository, rateLimiter, auditTrail, new PiiMasker(), events);

    @BeforeEach
    void setUp() {
        when(rateLimiter.tryConsume(USER_ID, RateLimitedAction.PROFILE_WRITE)).thenReturn(Decision.allow());
        when(repository.findById(USER_ID)).thenReturn(Optional.of(account("admin_cronos", "+525512345678", 4)));
    }

    @Test
    void appliesFullReplaceAuditsMaskedDiffAndAnnouncesRename() {
        var update = new ProfileUpdate("new_name", "Antón", null, new E164Phone("+14155552671"), ExpectedVersion.of(4));
        when(repository.applyProfile(USER_ID, update)).thenReturn(account("new_name", "+14155552671", 5));

        UserAccount result = useCase.execute(update);

        assertThat(result.version()).isEqualTo(5);
        ArgumentCaptor<AuditChange> audit = ArgumentCaptor.forClass(AuditChange.class);
        verify(auditTrail).record(audit.capture());
        assertThat(audit.getValue()).isInstanceOf(AuditChange.ProfileChanged.class);
        assertThat(audit.getValue().changes())
                .containsEntry("username", new FieldDiff("admin_cronos", "new_name"))
                .containsEntry("phoneNumber", new FieldDiff("+52******5678", "+14*****2671"))
                .doesNotContainKey("firstName");
        verify(events).publishEvent(new UsernameChanged(USER_ID, "admin_cronos", "new_name"));
    }

    @Test
    void duplicateUsernameIsA409OnFieldUsername() {
        when(repository.isUsernameTakenByOther("Taken", USER_ID)).thenReturn(true);

        var thrown = catchThrowableOfType(AccountDomainException.class,
                () -> useCase.execute(new ProfileUpdate("Taken", null, null, null, ExpectedVersion.ANY)));

        assertThat(thrown.primary()).isInstanceOfSatisfying(AccountDomainError.DuplicateUsername.class, e -> {
            assertThat(e.field()).isEqualTo("username");
            assertThat(e.code()).isEqualTo("DUPLICATE_RESOURCE");
        });
        verify(repository, never()).applyProfile(any(), any());
    }

    @Test
    void staleIfMatchIsAPreconditionFailure() {
        var thrown = catchThrowableOfType(AccountDomainException.class,
                () -> useCase.execute(new ProfileUpdate("admin_cronos", null, null, null, ExpectedVersion.of(3))));

        assertThat(thrown.primary()).isInstanceOf(AccountDomainError.VersionMismatch.class);
        verify(repository, never()).applyProfile(any(), any());
    }

    @Test
    void rateLimitedBeforeTouchingTheDatabase() {
        when(rateLimiter.tryConsume(USER_ID, RateLimitedAction.PROFILE_WRITE)).thenReturn(Decision.deny(90));

        var thrown = catchThrowableOfType(AccountDomainException.class,
                () -> useCase.execute(new ProfileUpdate("admin_cronos", null, null, null, ExpectedVersion.ANY)));

        assertThat(thrown.primary()).isInstanceOfSatisfying(AccountDomainError.WriteRateLimited.class,
                e -> assertThat(e.retryAfterSeconds()).isEqualTo(90));
        verify(repository, never()).findById(any());
    }

    @Test
    void identityComesOnlyFromTheCurrentUserProvider() {
        var update = new ProfileUpdate("admin_cronos", "A", "B", null, ExpectedVersion.ANY);
        when(repository.applyProfile(eq(USER_ID), any())).thenReturn(account("admin_cronos", null, 5));

        useCase.execute(update);

        verify(repository).applyProfile(USER_ID, update);
    }

    private static UserAccount account(String username, String phone, long version) {
        return new UserAccount(USER_ID, username, "admin@cronos.com", "Antón", "Admin", phone, null, true, true, false, 0,
                null, null, null, Set.of("SUPER_ADMIN"), LocalDateTime.now(), LocalDateTime.now(), version);
    }
}
