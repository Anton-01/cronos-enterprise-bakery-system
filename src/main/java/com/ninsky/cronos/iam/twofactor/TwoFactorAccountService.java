package com.ninsky.cronos.iam.twofactor;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.access.AccessVersions;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.policy.TwoFactorChanged;
import com.ninsky.cronos.iam.policy.TwoFactorRequirement;
import com.ninsky.cronos.iam.shared.KeyedRateLimiter;
import com.ninsky.cronos.iam.token.CredentialDelivery;
import com.ninsky.cronos.iam.twofactor.api.ConfirmEnrollmentRequest;
import com.ninsky.cronos.iam.twofactor.api.DisableTwoFactorRequest;
import com.ninsky.cronos.iam.twofactor.api.RecoveryCodesRequest;
import com.ninsky.cronos.iam.twofactor.api.TwoFactorEnrollment;
import com.ninsky.cronos.iam.twofactor.api.TwoFactorRecoveryCodes;
import com.ninsky.cronos.iam.twofactor.api.TwoFactorStatus;
import com.ninsky.cronos.iam.user.UserReadRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.infrastructure.config.security.SecurityProperties;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/** Contract §8.2: self-service TOTP for the signed-in user. Secrets and codes never reach a log. */
@Service
public class TwoFactorAccountService {

    static final Duration ENROLLMENT_TTL = Duration.ofMinutes(10);
    static final int MAX_CONFIRM_FAILURES = 5;
    static final int SECRET_BYTES = 20;
    static final String DISABLED_REASON = "TWO_FACTOR_DISABLED";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TwoFactorStore store;
    private final SecondFactor secondFactor;
    private final TwoFactorSecretCipher cipher;
    private final QrCodeRenderer qrCodes;
    private final TwoFactorRequirement requirement;
    private final UserReadRepository users;
    private final AccessVersions versions;
    private final OtherSessions otherSessions;
    private final AuditRecorder recorder;
    private final ApplicationEventPublisher events;
    private final PasswordEncoder passwords;
    private final String issuer;
    private final Clock clock;
    private final KeyedRateLimiter enrolments = new KeyedRateLimiter(10, Duration.ofHours(1));

    @SuppressWarnings("java:S107") // One collaborator per concern of the flow.
    public TwoFactorAccountService(TwoFactorStore store, SecondFactor secondFactor, TwoFactorSecretCipher cipher,
                                   QrCodeRenderer qrCodes, TwoFactorRequirement requirement, UserReadRepository users,
                                   AccessVersions versions, OtherSessions otherSessions, AuditRecorder recorder,
                                   ApplicationEventPublisher events, PasswordEncoder passwords,
                                   SecurityProperties security, Clock clock) {
        this.store = store;
        this.secondFactor = secondFactor;
        this.cipher = cipher;
        this.qrCodes = qrCodes;
        this.requirement = requirement;
        this.users = users;
        this.versions = versions;
        this.otherSessions = otherSessions;
        this.recorder = recorder;
        this.events = events;
        this.passwords = passwords;
        this.issuer = security.getTwoFactor().getIssuer();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TwoFactorStatus status(UUID userId) {
        boolean enabled = user(userId).twoFactorEnabled();
        return new TwoFactorStatus(enabled, requirement.isRequired(userId), requirement.requiredBy(userId),
                enabled ? TwoFactorStatus.TOTP : null,
                enabled ? store.enrolled(userId).map(TwoFactorStore.Enrolled::enrolledAt).orElse(null) : null,
                enabled ? store.remainingRecoveryCodes(userId) : 0);
    }

    @Transactional
    public TwoFactorEnrollment startEnrollment(UUID userId) {
        UserRow user = user(userId);
        if (user.twoFactorEnabled()) {
            throw ApiException.of(ApiErrorCode.TWO_FACTOR_ALREADY_ENABLED, null, "security.twoFactor.alreadyEnabled");
        }
        enrolments.acquire(userId);
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ENROLLMENT_TTL);
        store.startEnrollment(id, userId, cipher.seal(secret), now, expiresAt);

        String base32 = Base32.encode(secret);
        String account = user.email() != null ? user.email() : user.username();
        String uri = otpauthUri(issuer, account, base32);
        return new TwoFactorEnrollment(id, base32, uri, qrCodes.pngDataUri(uri), issuer, account, Totp.DIGITS,
                Totp.PERIOD_SECONDS, expiresAt);
    }

    /** Wrong codes are counted (and committed) before the 400; the 5th one consumes the enrolment. */
    @Transactional(noRollbackFor = ApiException.class)
    public TwoFactorRecoveryCodes confirm(UUID userId, ConfirmEnrollmentRequest request) {
        new Violations()
                .invalidIf(request.enrollmentId() == null, "enrollmentId", "api.validation.required")
                .invalidIf(request.code() == null || !request.code().matches("\\d{6}"), "code", "security.twoFactor.codeFormat")
                .throwIfAny();
        if (user(userId).twoFactorEnabled()) {
            throw ApiException.of(ApiErrorCode.TWO_FACTOR_ALREADY_ENABLED, null, "security.twoFactor.alreadyEnabled");
        }
        Instant now = clock.instant();
        TwoFactorStore.Enrollment enrollment = store.lockEnrollment(request.enrollmentId(), userId)
                .filter(e -> e.consumedAt() == null)
                .orElseThrow(() -> ApiException.notFound("security.twoFactor.enrollmentNotFound"));
        if (!enrollment.expiresAt().isAfter(now)) {
            throw ApiException.of(ApiErrorCode.ENROLLMENT_EXPIRED, null, "security.twoFactor.enrollmentExpired");
        }
        byte[] secret = cipher.open(enrollment.secretEnc())
                .orElseThrow(() -> new IllegalStateException("Enrolment secret cannot be decrypted"));
        OptionalLong step = Totp.matchingStep(secret, request.code(), now);
        if (step.isEmpty()) {
            if (store.registerFailure(enrollment.id()) >= MAX_CONFIRM_FAILURES) {
                store.consumeEnrollment(enrollment.id(), now);
            }
            throw ApiException.of(ApiErrorCode.INVALID_TOTP_CODE, "code", "security.twoFactor.invalidTotp");
        }
        store.enrol(userId, enrollment.secretEnc(), step.getAsLong(), now);
        store.consumeEnrollment(enrollment.id(), now);
        List<String> codes = secondFactor.issueRecoveryCodes(userId);
        changed(userId);
        audit(userId, AuditAction.USER_2FA_ENABLED, AuditSeverity.NOTICE, Map.of());
        return new TwoFactorRecoveryCodes(codes, status(userId));
    }

    @Transactional
    public TwoFactorStatus disable(UUID userId, DisableTwoFactorRequest request) {
        new Violations()
                .invalidIf(isBlank(request.password()), "password", "api.validation.required")
                .invalidIf(isBlank(request.code()), "code", "api.validation.required")
                .throwIfAny();
        UserRow user = user(userId);
        if (!user.twoFactorEnabled()) {
            throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, null, "security.twoFactor.notEnabled");
        }
        if (requirement.isRequired(userId)) {
            throw ApiException.of(ApiErrorCode.TWO_FACTOR_REQUIRED_BY_ROLE, null, "security.twoFactor.requiredByRole");
        }
        if (!store.passwordHash(userId).map(hash -> passwords.matches(request.password(), hash)).orElse(false)) {
            throw ApiException.of(ApiErrorCode.INVALID_PASSWORD, "password", "security.twoFactor.invalidPassword");
        }
        secondFactor.require(userId, request.code(), "code");
        store.remove(userId);
        int revoked = otherSessions.revoke(userId, DISABLED_REASON);
        changed(userId);
        audit(userId, AuditAction.USER_2FA_DISABLED, AuditSeverity.WARNING, Map.of("revokedSessions", revoked));
        events.publishEvent(new CredentialDelivery.SecurityNotice(userId, user.email(), user.displayName(), user.locale(),
                "twoFactorDisabled"));
        return status(userId);
    }

    @Transactional
    public TwoFactorRecoveryCodes regenerateRecoveryCodes(UUID userId, RecoveryCodesRequest request) {
        new Violations().invalidIf(isBlank(request.code()), "code", "api.validation.required").throwIfAny();
        if (!user(userId).twoFactorEnabled()) {
            throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, null, "security.twoFactor.notEnabled");
        }
        secondFactor.require(userId, request.code(), "code");
        List<String> codes = secondFactor.issueRecoveryCodes(userId);
        audit(userId, AuditAction.USER_2FA_RECOVERY_CODES_REGENERATED, AuditSeverity.NOTICE, Map.of());
        return new TwoFactorRecoveryCodes(codes, status(userId));
    }

    /**
     * Second factor at sign-in: a TOTP or a recovery code. Using a recovery code is audited like the
     * other sign-in events (independently, so it never waits on the login's own audit lock) and the
     * owner is told by email how many remain.
     */
    @Transactional
    public SecondFactor.Result verifySignIn(UUID userId, String code) {
        SecondFactor.Result result = secondFactor.verify(userId, code);
        if (result == SecondFactor.Result.RECOVERY_CODE) {
            UserRow user = user(userId);
            recorder.recordIndependently(AuditEvent.of(AuditAction.LOGIN_WITH_RECOVERY_CODE, AuditTargets.USER, userId, user.displayName())
                    .severity(AuditSeverity.WARNING).build());
            events.publishEvent(new CredentialDelivery.SecurityNotice(userId, user.email(), user.displayName(), user.locale(),
                    "recoveryCodeUsed", store.remainingRecoveryCodes(userId)));
        }
        return result;
    }

    /** Administrative reset: secret, recovery codes and pending enrolments go. The caller audits. */
    @Transactional
    public void reset(UUID userId) {
        store.remove(userId);
        events.publishEvent(new TwoFactorChanged(userId));
    }

    static String otpauthUri(String issuer, String account, String secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.PERIOD_SECONDS;
    }

    private void changed(UUID userId) {
        versions.bump(List.of(userId));
        events.publishEvent(new TwoFactorChanged(userId));
    }

    private void audit(UUID userId, AuditAction action, AuditSeverity severity, Map<String, Object> params) {
        recorder.record(AuditEvent.of(action, AuditTargets.USER, userId, user(userId).displayName())
                .severity(severity).params(params).build());
    }

    private UserRow user(UUID userId) {
        return users.find(userId).orElseThrow(() -> ApiException.notFound("iam.user.notFound"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
