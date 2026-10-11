package com.ninsky.cronos.identity.application;

import com.ninsky.cronos.account.profile.domain.E164Phone;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.port.auth.UserAuthLookupPort;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.AccountCreated;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.token.CredentialDelivery;
import com.ninsky.cronos.iam.token.IssuedToken;
import com.ninsky.cronos.iam.token.TokenPurpose;
import com.ninsky.cronos.iam.token.UserTokens;
import com.ninsky.cronos.iam.user.UserProfile;
import com.ninsky.cronos.iam.user.UserProfileRules;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.iam.user.UserWriteCustomRepository;
import com.ninsky.cronos.identity.api.RegisterRequest;
import com.ninsky.cronos.identity.api.RegistrationAvailability;
import com.ninsky.cronos.identity.api.RegistrationReceipt;
import com.ninsky.cronos.identity.api.VerifyEmailResult;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.identity.domain.RegistrationEvents;
import com.ninsky.cronos.identity.domain.SignInPolicy;
import com.ninsky.cronos.identity.domain.UsernameRules;
import com.ninsky.cronos.identity.infrastructure.AuthProperties;
import com.ninsky.cronos.identity.infrastructure.AuthRateLimiter;
import com.ninsky.cronos.identity.infrastructure.EmailVerificationCustomRepository;
import com.ninsky.cronos.identity.infrastructure.RegistrationCustomRepository;
import com.ninsky.cronos.identity.infrastructure.SignInPolicyProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Self-registration (§2.4) and email verification (§2.6). A new account is PENDING_APPROVAL until an administrator
 * decides; everything that informs people (receipt, verification link, reviewer alerts) is an event handled after
 * commit. A registration with the address of an existing account answers exactly like a success and emails the owner.
 */
@Slf4j
@Service
public class RegistrationService {

    static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserWriteCustomRepository writes;
    private final UserReadCustomRepository reads;
    private final UserAuthLookupPort lookup;
    private final RegistrationCustomRepository registrations;
    private final EmailVerificationCustomRepository verification;
    private final UserTokens tokens;
    private final PasswordPolicyGuard passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SignInPolicyProvider policies;
    private final AuthProperties properties;
    private final AuthRateLimiter rateLimiter;
    private final RequestContextUtil request;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher springEvents;
    private final DomainEventPublisher events;
    private final Clock clock;

    public RegistrationService(UserWriteCustomRepository writes, UserReadCustomRepository reads, UserAuthLookupPort lookup,
                               RegistrationCustomRepository registrations, EmailVerificationCustomRepository verification,
                               UserTokens tokens, PasswordPolicyGuard passwordPolicy, PasswordEncoder passwordEncoder,
                               SignInPolicyProvider policies, AuthProperties properties, AuthRateLimiter rateLimiter,
                               RequestContextUtil request, AuditRecorder audit, ApplicationEventPublisher springEvents,
                               DomainEventPublisher events, Clock clock) {
        this.writes = writes;
        this.reads = reads;
        this.lookup = lookup;
        this.registrations = registrations;
        this.verification = verification;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.policies = policies;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.request = request;
        this.audit = audit;
        this.springEvents = springEvents;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates a PENDING_APPROVAL account and its registration request.
     *
     * @throws ApiException RATE_LIMITED; VALIDATION_ERROR (every field at once); DUPLICATE_RESOURCE on {@code username};
     *                      PASSWORD_POLICY_VIOLATION on {@code password}
     */
    @Transactional
    public RegistrationReceipt register(RegisterRequest body) {
        rateLimiter.acquire(AuthRateLimiter.Limit.REGISTER_IP, request.getClientIp());
        Violations violations = new Violations();
        String username = UsernameRules.normalize(body.username());
        violations.invalidIf(!UsernameRules.wellFormed(username), "username", "auth.register.username.invalid");
        String email = UserProfileRules.normalizeEmail(body.email());
        violations.invalidIf(email == null, "email", "auth.register.email.invalid");
        String phone = null;
        if (body.phoneNumber() != null && !body.phoneNumber().isBlank()) {
            phone = E164Phone.normalize(body.phoneNumber().trim()).orElse(null);
            violations.invalidIf(phone == null, "phoneNumber", "auth.register.phone.invalid");
        }
        violations.invalidIf(!properties.termsVersion().equals(body.acceptedTermsVersion()), "acceptedTermsVersion",
                "auth.register.terms.outdated", properties.termsVersion());
        violations.throwIfAny();
        if (UsernameRules.reserved(username) || reads.usernameTaken(username, null)) {
            throw ApiException.of(ApiErrorCode.DUPLICATE_RESOURCE, "username", "auth.register.username.taken");
        }
        passwordPolicy.require("password", body.password(), username, email, null);

        Instant now = TenantTime.now(clock);
        SignInPolicy policy = policies.current();
        if (reads.emailTaken(email, null)) {
            // Never reveal that the address has an account: same answer, the owner is told instead.
            lookup.findByUsernameOrEmail(email)
                    .ifPresent(owner -> events.publish(new IdentityEvents.RegistrationAttemptOnExistingEmail(owner.id())));
            log.info("Self-registration with an address that already has an account");
            return new RegistrationReceipt("REG-%d-%06d".formatted(TenantTime.today(clock).getYear(), RANDOM.nextInt(1_000_000)),
                    email, UserStatus.PENDING_APPROVAL.name(), policy.requireEmailVerification(), now);
        }

        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile(username, email, body.firstName().trim(), body.lastName().trim(), phone, null, null, null,
                body.locale(), null, false);
        writes.insertSelfRegistered(userId, profile, passwordEncoder.encode(body.password()), body.acceptedTermsVersion(), now);
        springEvents.publishEvent(new AccountCreated(userId));

        UUID registrationId = UUID.randomUUID();
        String reference = registrations.nextReference(TenantTime.today(clock).getYear());
        registrations.insert(new RegistrationCustomRepository.NewRegistration(registrationId, reference, userId,
                blankToNull(body.businessName()), blankToNull(body.message()), request.getClientIp(), request.getUserAgent(), now,
                now.plus(policy.registrationTtl())));
        if (policy.requireEmailVerification()) {
            sendVerification(userId, email, profile.displayName(), body.locale());
        }
        audit.record(AuditEvent.of(AuditAction.REGISTRATION_SUBMITTED, AuditTargets.USER, userId, profile.displayName())
                .params(Map.of("detail", reference)).build());
        events.publish(new RegistrationEvents.RegistrationSubmitted(registrationId, userId, reference, policy.requireEmailVerification()));
        return new RegistrationReceipt(reference, email, UserStatus.PENDING_APPROVAL.name(), policy.requireEmailVerification(), now);
    }

    /**
     * Username: real availability (usernames are public). Email: {@code true} or null, never {@code false}.
     *
     * @throws ApiException RATE_LIMITED
     */
    @Transactional(readOnly = true)
    public RegistrationAvailability availability(String username, String email) {
        rateLimiter.acquire(AuthRateLimiter.Limit.AVAILABILITY_IP, request.getClientIp());
        Boolean usernameAvailable = null;
        if (username != null) {
            String normalized = UsernameRules.normalize(username);
            usernameAvailable = UsernameRules.wellFormed(normalized) && !UsernameRules.reserved(normalized)
                    && !reads.usernameTaken(normalized, null);
        }
        Boolean emailAvailable = null;
        if (email != null) {
            String normalized = UserProfileRules.normalizeEmail(email);
            emailAvailable = normalized != null && !reads.emailTaken(normalized, null) ? Boolean.TRUE : null;
        }
        return new RegistrationAvailability(usernameAvailable, emailAvailable);
    }

    /**
     * Confirms the address of a verification link.
     *
     * @throws ApiException INVALID_TOKEN for an unknown or expired link
     */
    @Transactional
    public VerifyEmailResult verifyEmail(String token) {
        Optional<UUID> holder = tokens.consume(token, TokenPurpose.EMAIL_VERIFICATION);
        if (holder.isEmpty()) {
            UUID spentBy = verification.holderOfAnyLink(token, TokenPurpose.EMAIL_VERIFICATION)
                    .filter(verification::isVerified)
                    .orElseThrow(() -> ApiException.of(ApiErrorCode.INVALID_TOKEN, "token", "security.token.invalid"));
            return new VerifyEmailResult("ALREADY_VERIFIED", pendingApproval(spentBy));
        }
        UUID userId = holder.get();
        boolean changed = verification.markVerified(userId);
        boolean pending = pendingApproval(userId);
        if (changed) {
            audit.record(AuditEvent.of(AuditAction.EMAIL_VERIFIED, AuditTargets.USER, userId,
                    reads.find(userId).map(row -> row.displayName()).orElse(null)).build());
            events.publish(new IdentityEvents.EmailVerified(userId, pending));
        }
        return new VerifyEmailResult(changed ? "VERIFIED" : "ALREADY_VERIFIED", pending);
    }

    /** Sends a new verification link when an unverified account has {@code email}; silent otherwise. */
    @Transactional
    public void resendVerification(String email, String locale) {
        rateLimiter.acquire(AuthRateLimiter.Limit.RECOVERY_IP, request.getClientIp());
        String normalized = UserProfileRules.normalizeEmail(email);
        if (normalized == null) {
            return;
        }
        rateLimiter.acquire(AuthRateLimiter.Limit.RECOVERY_IDENTIFIER, normalized);
        lookup.findByUsernameOrEmail(normalized)
                .filter(account -> !verification.isVerified(account.id()))
                .flatMap(account -> reads.find(account.id()))
                .ifPresent(row -> sendVerification(row.id(), row.email(), row.displayName(), locale != null ? locale : row.locale()));
    }

    /** Administrator resend (§3.2): same link, no rate limit beyond the endpoint's permission. */
    @Transactional
    public void resendVerificationFor(UUID userId) {
        reads.find(userId).filter(row -> !row.emailVerified())
                .ifPresent(row -> sendVerification(row.id(), row.email(), row.displayName(), row.locale()));
    }

    private void sendVerification(UUID userId, String email, String displayName, String locale) {
        IssuedToken token = tokens.issue(userId, TokenPurpose.EMAIL_VERIFICATION, VERIFICATION_TTL);
        springEvents.publishEvent(new CredentialDelivery.EmailVerification(userId, email, displayName, locale, token.rawToken(),
                token.expiresAt()));
    }

    private boolean pendingApproval(UUID userId) {
        return reads.find(userId).map(row -> row.status() == UserStatus.PENDING_APPROVAL).orElse(false);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
