package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.domain.model.auth.PasswordResetToken;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.port.auth.PasswordResetTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserAuthLookupPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Masking;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.signin.AccountStanding;
import com.ninsky.cronos.iam.signin.AccountStandingCustomRepository;
import com.ninsky.cronos.iam.signin.CredentialWriter;
import com.ninsky.cronos.iam.token.CredentialDelivery;
import com.ninsky.cronos.iam.token.IssuedToken;
import com.ninsky.cronos.iam.token.TokenPurpose;
import com.ninsky.cronos.iam.token.UserTokens;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.iam.user.UserStatusWriter;
import com.ninsky.cronos.identity.application.PasswordPolicyGuard;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.identity.domain.LoginIdentifier;
import com.ninsky.cronos.identity.infrastructure.AuthRateLimiter;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Self-service password recovery (§2.5). Links are single-use {@code user_tokens} PASSWORD_RESET rows (SHA-256 hash
 * stored, 30 min); answers never reveal whether an account exists. Completion validates the new password before the
 * link is spent, revokes every session and publishes {@code identity.password-changed}.
 */
@Slf4j
@Service
public class PasswordResetService {

    static final Duration LINK_TTL = Duration.ofMinutes(30);
    private static final Set<UserStatus> RESETTABLE = EnumSet.of(UserStatus.ACTIVE, UserStatus.LOCKED);

    /** What the reset form shows before asking for the new password. */
    public record ResetLinkInfo(boolean valid, String maskedEmail, Instant expiresAt) {
    }

    private final UserRepositoryPort userRepository;
    private final UserAuthLookupPort lookup;
    private final PasswordResetTokenRepositoryPort legacyTokens;
    private final UserTokens userTokens;
    private final PasswordPolicyGuard passwordPolicy;
    private final CredentialWriter credentials;
    private final UserStatusWriter statusWriter;
    private final SessionRevoker sessionRevoker;
    private final AccountStandingCustomRepository standings;
    private final UserDirectory userDirectory;
    private final AuditRecorder recorder;
    private final org.springframework.context.ApplicationEventPublisher springEvents;
    private final DomainEventPublisher events;
    private final AuthRateLimiter rateLimiter;
    private final RequestContextUtil request;
    private final Clock clock;

    public PasswordResetService(UserRepositoryPort userRepository, UserAuthLookupPort lookup, PasswordResetTokenRepositoryPort legacyTokens,
                                UserTokens userTokens, PasswordPolicyGuard passwordPolicy, CredentialWriter credentials,
                                UserStatusWriter statusWriter, SessionRevoker sessionRevoker, AccountStandingCustomRepository standings,
                                UserDirectory userDirectory, AuditRecorder recorder,
                                org.springframework.context.ApplicationEventPublisher springEvents, DomainEventPublisher events,
                                AuthRateLimiter rateLimiter, RequestContextUtil request, Clock clock) {
        this.userRepository = userRepository;
        this.lookup = lookup;
        this.legacyTokens = legacyTokens;
        this.userTokens = userTokens;
        this.passwordPolicy = passwordPolicy;
        this.credentials = credentials;
        this.statusWriter = statusWriter;
        this.sessionRevoker = sessionRevoker;
        this.standings = standings;
        this.userDirectory = userDirectory;
        this.recorder = recorder;
        this.springEvents = springEvents;
        this.events = events;
        this.rateLimiter = rateLimiter;
        this.request = request;
        this.clock = clock;
    }

    /**
     * Issues a reset link when {@code identifier} (username or email) belongs to an ACTIVE or LOCKED account; silent
     * otherwise. Rate-limited per identifier and IP.
     *
     * @param locale the language of the email ({@code es-MX}|{@code en}); null uses the account's
     * @throws ApiException RATE_LIMITED
     */
    @Transactional
    public void processForgotPassword(String identifier, String locale) {
        Optional<LoginIdentifier> normalized = LoginIdentifier.resolve(identifier, null);
        rateLimiter.acquire(AuthRateLimiter.Limit.RECOVERY_IP, request.getClientIp());
        if (normalized.isEmpty()) {
            return;
        }
        rateLimiter.acquire(AuthRateLimiter.Limit.RECOVERY_IDENTIFIER, normalized.get().value());
        lookup.findByUsernameOrEmail(normalized.get().value()).ifPresent(account -> standings.find(account.id())
                .filter(standing -> RESETTABLE.contains(standing.status()))
                .ifPresent(standing -> issueLink(account, standing, locale)));
    }

    /** Legacy entry point (email only, account locale). */
    @Transactional
    public void processForgotPassword(String email) {
        processForgotPassword(email, null);
    }

    /** Validity of a link before the form is shown; an invalid, used or expired link is {@code valid = false}. */
    @Transactional(readOnly = true)
    public ResetLinkInfo linkInfo(String token) {
        return userTokens.peek(token, TokenPurpose.PASSWORD_RESET)
                .flatMap(pending -> userRepository.findById(pending.userId())
                        .map(user -> new ResetLinkInfo(true, Masking.email(user.getEmail()), pending.expiresAt())))
                .orElse(new ResetLinkInfo(false, null, null));
    }

    /**
     * Sets a new password with a reset link.
     *
     * @throws ApiException VALIDATION_ERROR on {@code confirmPassword}; INVALID_TOKEN; PASSWORD_POLICY_VIOLATION or
     *                      PASSWORD_REUSED on {@code newPassword}
     */
    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        if (confirmPassword != null && !confirmPassword.equals(newPassword)) {
            throw ApiException.invalid("confirmPassword", "account.password.confirmationMismatch");
        }
        UUID userId = userTokens.peek(token, TokenPurpose.PASSWORD_RESET).map(UserTokens.PendingToken::userId)
                .or(() -> legacyHolder(token))
                .orElseThrow(PasswordResetService::invalidToken);
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        passwordPolicy.require("newPassword", newPassword, user.getUsername(), user.getEmail(), userId);
        // Spend the link only once the new password is acceptable.
        boolean spent = userTokens.consume(token, TokenPurpose.PASSWORD_RESET).isPresent() || consumeLegacy(token);
        if (!spent) {
            throw invalidToken();
        }
        credentials.setPassword(userId, newPassword, false);
        // A completed reset proves control of the mailbox: lift an (automatic or manual) lock.
        standings.find(userId).filter(standing -> standing.status() == UserStatus.LOCKED).map(AccountStanding::userId)
                .ifPresent(id -> {
                    statusWriter.apply(id, new UserStatusWriter.Change(UserStatus.ACTIVE, null, "PASSWORD_RESET", null, null), null);
                    events.publish(new IdentityEvents.AccountUnlocked(id, "PASSWORD_RESET"));
                });
        sessionRevoker.revokeAll(userId, "PASSWORD_RESET");
        String label = labelOf(user);
        recorder.record(AuditEvent.of(AuditAction.PASSWORD_RESET_COMPLETED, AuditTargets.USER, userId, label).build());
        events.publish(new IdentityEvents.PasswordChanged(userId, TenantTime.now(clock), "RESET"));
        events.publish(new IdentityEvents.SessionRevoked(userId, null, "PASSWORD_RESET"));
        log.info("Password reset completed for user {}", userId);
    }

    /** Legacy two-field form (no confirmation). */
    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {
        resetPassword(token, newPassword, null);
    }

    private void issueLink(AuthUserProjection account, AccountStanding standing, String locale) {
        User user = userRepository.findById(account.id()).orElseThrow();
        IssuedToken token = userTokens.issue(user.getId(), TokenPurpose.PASSWORD_RESET, LINK_TTL);
        springEvents.publishEvent(new CredentialDelivery.PasswordResetLink(user.getId(), user.getEmail(), labelOf(user),
                locale != null ? locale : standing.locale(), token.rawToken(), token.expiresAt()));
        log.info("Password reset link issued for user {}", user.getId());
    }

    private Optional<UUID> legacyHolder(String token) {
        LocalDateTime now = TenantTime.nowLocal(clock);
        return legacyTokens.findByToken(token)
                .filter(legacy -> !legacy.isUsed() && legacy.getExpiresAt().isAfter(now))
                .map(PasswordResetToken::getUserId);
    }

    /** Pre-IAM raw tokens still in flight. */
    private boolean consumeLegacy(String token) {
        LocalDateTime now = TenantTime.nowLocal(clock);
        return legacyTokens.findByToken(token)
                .filter(legacy -> !legacy.isUsed() && legacy.getExpiresAt().isAfter(now))
                .map(legacy -> {
                    legacy.setUsed(true);
                    legacyTokens.save(legacy);
                    return true;
                })
                .orElse(false);
    }

    private static ApiException invalidToken() {
        return ApiException.of(ApiErrorCode.INVALID_TOKEN, "token", "security.token.invalid");
    }

    private String labelOf(User user) {
        return userDirectory.ref(user.getId()).map(UserRef::displayName).orElse(user.getUsername());
    }
}
