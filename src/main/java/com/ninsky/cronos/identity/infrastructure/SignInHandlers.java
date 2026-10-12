package com.ninsky.cronos.identity.infrastructure;

import com.ninsky.cronos.application.service.auth.AccountLockoutService;
import com.ninsky.cronos.application.service.auth.SessionManagementService;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.signin.CredentialCustomRepository;
import com.ninsky.cronos.iam.signin.SignInJournal;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.identity.domain.SignInOutcome;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;

/**
 * Everything a sign-in causes besides the session itself, moved off the request thread (performance-latency P0-5).
 * Each handler is its own outbox consumer, runs in its own transaction with its {@code processed_events} row (at most
 * once per event) and carries the event's correlation, causation and trace ids through {@code EventEnvelope}.
 * <ul>
 *   <li>{@code identity.user-signed-in}: login history + audit, device fingerprint (and the new-device alert),
 *       {@code last_login_at} / durable counter reset, concurrent-session limit;</li>
 *   <li>{@code identity.sign-in-failed}: login history + audit, durable failure counter;</li>
 *   <li>{@code identity.account-locked} / {@code identity.account-unlocked}: durable lock state, Redis lock window.</li>
 * </ul>
 */
@Configuration
public class SignInHandlers {

    /** Devices not seen for this long count as new (§2.3.5). */
    static final Duration NEW_DEVICE_MEMORY = Duration.ofDays(90);

    private final SignInJournal journal;
    private final CredentialCustomRepository credentials;

    public SignInHandlers(SignInJournal journal, CredentialCustomRepository credentials) {
        this.journal = journal;
        this.credentials = credentials;
    }

    @Bean
    OutboxHandler<IdentityEvents.UserSignedIn> signInHistory() {
        return OutboxHandlers.of(IdentityEvents.UserSignedIn.class, "SignInHistory", (event, envelope) ->
                journal.record(envelope.id(), event.userId(), SignInOutcome.SUCCESS, null, event.twoFactorUsed(), event.context()));
    }

    @Bean
    OutboxHandler<IdentityEvents.UserSignedIn> signInDevice(DeviceRecognition devices) {
        return OutboxHandlers.of(IdentityEvents.UserSignedIn.class, "SignInDevice", (event, envelope) -> {
            if (event.context() != null) {
                devices.recognise(event.userId(), event.context(), NEW_DEVICE_MEMORY);
            }
        });
    }

    @Bean
    OutboxHandler<IdentityEvents.UserSignedIn> signInLastLogin() {
        return OutboxHandlers.of(IdentityEvents.UserSignedIn.class, "SignInLastLogin", (event, envelope) ->
                credentials.recordSuccessfulLogin(event.userId(), TenantTime.toLocal(at(event.context(), envelope.occurredAt()))));
    }

    @Bean
    OutboxHandler<IdentityEvents.UserSignedIn> signInSessionLimit(SessionManagementService sessions, SecurityPolicyProvider policies) {
        return OutboxHandlers.of(IdentityEvents.UserSignedIn.class, "SignInSessionLimit", (event, envelope) ->
                sessions.enforceConcurrencyLimit(event.userId(), policies.current().maxConcurrentSessions(),
                        TenantTime.toLocal(at(event.context(), envelope.occurredAt()))));
    }

    @Bean
    OutboxHandler<IdentityEvents.SignInFailed> signInFailureHistory() {
        return OutboxHandlers.of(IdentityEvents.SignInFailed.class, "SignInFailureHistory", (event, envelope) -> {
            journal.record(envelope.id(), event.userId(), event.outcome(), event.failureReason(), event.twoFactorUsed(), event.context());
            if (event.userId() != null && event.failedAttempts() != null) {
                credentials.recordFailedAttempts(event.userId(), event.failedAttempts(),
                        TenantTime.toLocal(at(event.context(), envelope.occurredAt())));
            }
        });
    }

    @Bean
    OutboxHandler<IdentityEvents.AccountLocked> accountLockState(AccountLockoutService lockout, UserDirectory users) {
        return OutboxHandlers.of(IdentityEvents.AccountLocked.class, "AccountLockState", (event, envelope) ->
                lockout.applyLock(event, users.ref(event.userId()).map(UserRef::displayName).orElse(null)));
    }

    /** Any unlock (expiry, password reset, administrator) also ends the Redis lock window. */
    @Bean
    OutboxHandler<IdentityEvents.AccountUnlocked> accountUnlockState(LoginAttemptStore attempts) {
        return OutboxHandlers.of(IdentityEvents.AccountUnlocked.class, "AccountUnlockState",
                (event, envelope) -> attempts.unlock(event.userId()));
    }

    private static Instant at(IdentityEvents.SignInContext context, Instant fallback) {
        return context != null && context.at() != null ? context.at() : fallback;
    }
}
