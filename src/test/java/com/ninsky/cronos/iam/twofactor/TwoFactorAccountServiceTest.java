package com.ninsky.cronos.iam.twofactor;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.access.AccessVersions;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.policy.TwoFactorChanged;
import com.ninsky.cronos.iam.policy.TwoFactorRequirement;
import com.ninsky.cronos.iam.token.CredentialDelivery;
import com.ninsky.cronos.iam.twofactor.api.ConfirmEnrollmentRequest;
import com.ninsky.cronos.iam.twofactor.api.DisableTwoFactorRequest;
import com.ninsky.cronos.iam.twofactor.api.RecoveryCodesRequest;
import com.ninsky.cronos.iam.twofactor.api.TwoFactorEnrollment;
import com.ninsky.cronos.iam.twofactor.api.TwoFactorRecoveryCodes;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.infrastructure.config.security.SecurityProperties;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwoFactorAccountServiceTest {

    private static final UUID USER = UUID.fromString("5f3b1c2d-8e9a-4b7c-9d0e-1f2a3b4c5d6e");
    private static final UUID ENROLLMENT = UUID.fromString("6c1d0e2f-3a4b-4c5d-8e9f-0a1b2c3d4e5f");
    private static final Instant NOW = Instant.parse("2026-10-05T19:14:00Z");
    private static final byte[] SECRET = "12345678901234567890".getBytes();
    private static final byte[] SEALED = {7, 7, 7};

    private final TwoFactorCustomRepository store = mock(TwoFactorCustomRepository.class);
    private final SecondFactor secondFactor = mock(SecondFactor.class);
    private final TwoFactorSecretCipher cipher = mock(TwoFactorSecretCipher.class);
    private final TwoFactorRequirement requirement = mock(TwoFactorRequirement.class);
    private final UserReadCustomRepository users = mock(UserReadCustomRepository.class);
    private final AccessVersions versions = mock(AccessVersions.class);
    private final OtherSessions otherSessions = mock(OtherSessions.class);
    private final AuditRecorder recorder = mock(AuditRecorder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private TwoFactorAccountService service;

    @BeforeEach
    void setUp() {
        SecurityProperties security = new SecurityProperties();
        security.getTwoFactor().setIssuer("Cronos");
        service = new TwoFactorAccountService(store, secondFactor, cipher, content -> "data:image/png;base64,QR", requirement,
                users, versions, otherSessions, recorder, events, passwords, security, Clock.fixed(NOW, ZoneOffset.UTC));
        user(false);
        when(requirement.requiredBy(USER)).thenReturn(List.of("Administrador"));
    }

    private void user(boolean twoFactorEnabled) {
        when(users.find(USER)).thenReturn(Optional.of(new UserRow(USER, "admin", "admin@cronos.com", "Ana", "Ruiz", null,
                null, null, null, null, "es-MX", UserStatus.ACTIVE, null, null, null, null, null, null, false,
                twoFactorEnabled, false, true, 0, null, null, NOW, null, NOW, null, 0)));
    }

    private void pending(Instant expiresAt, Instant consumedAt) {
        when(store.lockEnrollment(ENROLLMENT, USER)).thenReturn(Optional.of(
                new TwoFactorCustomRepository.Enrollment(ENROLLMENT, USER, SEALED, 0, expiresAt, consumedAt)));
        when(cipher.open(SEALED)).thenReturn(Optional.of(SECRET));
    }

    private static ApiErrorCode codeOf(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e.primaryCode();
        }
        throw new AssertionError("expected an ApiException");
    }

    @Test
    void enrolmentReturnsSecretUriAndServerSideQr() {
        when(cipher.seal(any())).thenReturn(SEALED);
        TwoFactorEnrollment enrollment = service.startEnrollment(USER);
        assertThat(enrollment.secret()).hasSize(32).matches("[A-Z2-7]+");
        assertThat(enrollment.otpauthUri()).isEqualTo("otpauth://totp/Cronos:admin%40cronos.com?secret=" + enrollment.secret()
                + "&issuer=Cronos&algorithm=SHA1&digits=6&period=30");
        assertThat(enrollment.qrCodeDataUri()).startsWith("data:image/png;base64,");
        assertThat(enrollment.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(enrollment.toString()).doesNotContain(enrollment.secret());
        verify(store).startEnrollment(enrollment.enrollmentId(), USER, SEALED, NOW, NOW.plusSeconds(600));
    }

    @Test
    void enrolmentIsRefusedWhenAlreadyOnAndRateLimited() {
        user(true);
        assertThat(codeOf(() -> service.startEnrollment(USER))).isEqualTo(ApiErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        user(false);
        when(cipher.seal(any())).thenReturn(SEALED);
        for (int i = 0; i < 10; i++) {
            service.startEnrollment(USER);
        }
        assertThat(codeOf(() -> service.startEnrollment(USER))).isEqualTo(ApiErrorCode.RATE_LIMITED);
    }

    @Test
    void confirmValidatesEveryFieldAtOnce() {
        assertThatThrownBy(() -> service.confirm(USER, new ConfirmEnrollmentRequest(null, "12a")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.violations())
                        .extracting(ApiException.Violation::field).containsExactly("enrollmentId", "code"));
    }

    @Test
    void confirmTurnsTwoFactorOnInOneGo() {
        pending(NOW.plusSeconds(60), null);
        String code = Totp.code(SECRET, Totp.step(NOW));
        when(secondFactor.issueRecoveryCodes(USER)).thenReturn(List.of("7KQ4-M2XD"));
        when(store.enrolled(USER)).thenReturn(Optional.of(new TwoFactorCustomRepository.Enrolled(SEALED, Totp.step(NOW), NOW)));
        when(store.remainingRecoveryCodes(USER)).thenReturn(10);
        when(requirement.isRequired(USER)).thenReturn(true);
        user(false);

        TwoFactorRecoveryCodes result = service.confirm(USER, new ConfirmEnrollmentRequest(ENROLLMENT, code));

        assertThat(result.recoveryCodes()).containsExactly("7KQ4-M2XD");
        verify(store).enrol(USER, SEALED, Totp.step(NOW), NOW);
        verify(store).consumeEnrollment(ENROLLMENT, NOW);
        verify(versions).bump(List.of(USER));
        verify(events).publishEvent(new TwoFactorChanged(USER));
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.USER_2FA_ENABLED);
        assertThat(audit.getValue().severity()).isEqualTo(AuditSeverity.NOTICE);
    }

    @Test
    void confirmRejectsUnknownExpiredAndWrongCodes() {
        String code = Totp.code(SECRET, Totp.step(NOW));
        assertThat(codeOf(() -> service.confirm(USER, new ConfirmEnrollmentRequest(ENROLLMENT, code))))
                .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);
        pending(NOW.minusSeconds(1), null);
        assertThat(codeOf(() -> service.confirm(USER, new ConfirmEnrollmentRequest(ENROLLMENT, code))))
                .isEqualTo(ApiErrorCode.ENROLLMENT_EXPIRED);
        pending(NOW.plusSeconds(60), NOW);
        assertThat(codeOf(() -> service.confirm(USER, new ConfirmEnrollmentRequest(ENROLLMENT, code))))
                .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void fifthWrongCodeConsumesTheEnrolment() {
        pending(NOW.plusSeconds(60), null);
        String wrong = Totp.code(SECRET, Totp.step(NOW) + 5);
        when(store.registerFailure(ENROLLMENT)).thenReturn(4, 5);
        assertThat(codeOf(() -> service.confirm(USER, new ConfirmEnrollmentRequest(ENROLLMENT, wrong))))
                .isEqualTo(ApiErrorCode.INVALID_TOTP_CODE);
        verify(store, never()).consumeEnrollment(any(), any());
        assertThat(codeOf(() -> service.confirm(USER, new ConfirmEnrollmentRequest(ENROLLMENT, wrong))))
                .isEqualTo(ApiErrorCode.INVALID_TOTP_CODE);
        verify(store).consumeEnrollment(ENROLLMENT, NOW);
        verify(store, never()).enrol(any(), any(), anyLong(), any());
    }

    @Test
    void disableIsRefusedWhenMandatoryOrPasswordWrong() {
        user(true);
        when(requirement.isRequired(USER)).thenReturn(true);
        assertThat(codeOf(() -> service.disable(USER, new DisableTwoFactorRequest("pw", "123456"))))
                .isEqualTo(ApiErrorCode.TWO_FACTOR_REQUIRED_BY_ROLE);
        when(requirement.isRequired(USER)).thenReturn(false);
        when(store.passwordHash(USER)).thenReturn(Optional.of("hash"));
        when(passwords.matches("pw", "hash")).thenReturn(false);
        assertThat(codeOf(() -> service.disable(USER, new DisableTwoFactorRequest("pw", "123456"))))
                .isEqualTo(ApiErrorCode.INVALID_PASSWORD);
        verify(store, never()).remove(USER);
    }

    @Test
    void disableRemovesEverythingSignsOutOtherSessionsAndNotifies() {
        user(true);
        when(store.passwordHash(USER)).thenReturn(Optional.of("hash"));
        when(passwords.matches("pw", "hash")).thenReturn(true);
        when(otherSessions.revoke(USER, TwoFactorAccountService.DISABLED_REASON)).thenReturn(2);

        service.disable(USER, new DisableTwoFactorRequest("pw", "7KQ4-M2XD"));

        verify(secondFactor).require(USER, "7KQ4-M2XD", "code");
        verify(store).remove(USER);
        verify(versions).bump(List.of(USER));
        verify(events).publishEvent(any(CredentialDelivery.SecurityNotice.class));
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.USER_2FA_DISABLED);
        assertThat(audit.getValue().severity()).isEqualTo(AuditSeverity.WARNING);
    }

    @Test
    void disableValidatesFieldsTogether() {
        assertThatThrownBy(() -> service.disable(USER, new DisableTwoFactorRequest(" ", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.violations())
                        .extracting(ApiException.Violation::field).containsExactly("password", "code"));
    }

    @Test
    void regeneratingNeedsASecondFactorAndAudits() {
        user(true);
        when(secondFactor.issueRecoveryCodes(USER)).thenReturn(List.of("AAAA-BBBB"));
        TwoFactorRecoveryCodes codes = service.regenerateRecoveryCodes(USER, new RecoveryCodesRequest("123456"));
        assertThat(codes.recoveryCodes()).containsExactly("AAAA-BBBB");
        assertThat(codes.toString()).doesNotContain("AAAA-BBBB");
        verify(secondFactor).require(USER, "123456", "code");
        verify(recorder).record(any());
    }

    @Test
    void signInWithARecoveryCodeIsAuditedAndMailed() {
        when(secondFactor.verify(USER, "7KQ4-M2XD")).thenReturn(SecondFactor.Result.RECOVERY_CODE);
        when(store.remainingRecoveryCodes(USER)).thenReturn(9);
        assertThat(service.verifySignIn(USER, "7KQ4-M2XD").accepted()).isTrue();
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder).recordIndependently(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOGIN_WITH_RECOVERY_CODE);
        verify(events).publishEvent(any(CredentialDelivery.SecurityNotice.class));

        when(secondFactor.verify(eq(USER), anyString())).thenReturn(SecondFactor.Result.TOTP);
        service.verifySignIn(USER, "123456");
        verify(recorder).recordIndependently(any());
    }

    @Test
    void statusReflectsRequirementAndEnrolment() {
        when(requirement.isRequired(USER)).thenReturn(true);
        assertThat(service.status(USER)).satisfies(status -> {
            assertThat(status.enabled()).isFalse();
            assertThat(status.required()).isTrue();
            assertThat(status.requiredBy()).containsExactly("Administrador");
            assertThat(status.method()).isNull();
            assertThat(status.recoveryCodesRemaining()).isZero();
        });
    }
}
