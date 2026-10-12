package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.signin.AccountStanding;
import com.ninsky.cronos.iam.signin.AuthProjectionCache;
import com.ninsky.cronos.iam.signin.CredentialCustomRepository;
import com.ninsky.cronos.iam.user.StatusReason;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.iam.user.UserStatusWriter;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.identity.domain.SignInPolicy;
import com.ninsky.cronos.identity.infrastructure.SignInPolicyProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Progressive account lockout (§2.3.1). Failures are counted in a sliding window of {@code failureWindowMinutes};
 * reaching {@code maxFailedAttempts} locks the account for {@code lockoutMinutes × multiplier^(lockCount-1)}, capped by
 * {@code lockoutMaxMinutes}; the lock count decays after a day without locks. Locking publishes
 * {@code identity.account-locked} (email + security notification), an expired lock {@code identity.account-unlocked}.
 */
@Slf4j
@Service
public class AccountLockoutService {

    static final String AUTO_LOCKOUT = "AUTO_LOCKOUT";
    static final String AUTO_UNLOCK = "AUTO_UNLOCK";

    /** Outcome of one failure: whether it locked the account, attempts left before a lock, and the lock end. */
    public record FailureResult(boolean locked, int remainingAttempts, Instant lockedUntil) {
    }

    private final CredentialCustomRepository credentials;
    private final SignInPolicyProvider policies;
    private final UserStatusWriter statusWriter;
    private final AuditRecorder recorder;
    private final AuthProjectionCache authCache;
    private final DomainEventPublisher events;
    private final Clock clock;

    public AccountLockoutService(CredentialCustomRepository credentials, SignInPolicyProvider policies, UserStatusWriter statusWriter,
                                 AuditRecorder recorder, AuthProjectionCache authCache, DomainEventPublisher events, Clock clock) {
        this.credentials = credentials;
        this.policies = policies;
        this.statusWriter = statusWriter;
        this.recorder = recorder;
        this.authCache = authCache;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Counts one failed sign-in (or screen unlock). Only ACTIVE accounts are locked.
     */
    public FailureResult registerFailure(UUID userId, UserStatus status, String label) {
        Instant now = TenantTime.now(clock);
        SignInPolicy policy = policies.current();
        CredentialCustomRepository.FailureState state = credentials
                .registerFailure(userId, now, now.minus(policy.failureWindow()), TenantTime.toLocal(now))
                .orElse(new CredentialCustomRepository.FailureState(0, 0, null));
        int remaining = Math.max(0, policy.maxFailedAttempts() - state.attempts());
        if (status != UserStatus.ACTIVE || remaining > 0) {
            return new FailureResult(false, remaining, null);
        }
        boolean remembered = state.lastLockedAt() != null && state.lastLockedAt().isAfter(now.minus(policy.lockMemory()));
        int lockCount = (remembered ? state.lockCount() : 0) + 1;
        Duration duration = policy.schedule().durationFor(lockCount);
        Instant until = now.plus(duration);
        statusWriter.apply(userId, new UserStatusWriter.Change(UserStatus.LOCKED, StatusReason.SECURITY_INCIDENT, AUTO_LOCKOUT,
                until, null), null);
        credentials.recordLock(userId, lockCount, now);
        authCache.evictNow();
        recorder.recordIndependently(AuditEvent.of(AuditAction.USER_LOCKED, AuditTargets.USER, userId, label)
                .severity(AuditSeverity.CRITICAL)
                .params(Map.of("detail", state.attempts(), "until", until.toString(), "lockCount", lockCount))
                .build());
        events.publish(new IdentityEvents.AccountLocked(userId, until, lockCount, state.attempts()));
        log.warn("Account {} locked (lock #{}) after {} failed attempts until {}", userId, lockCount, state.attempts(), until);
        return new FailureResult(true, 0, until);
    }

    /** Re-activates an account whose automatic lock already expired; returns the refreshed standing. */
    public AccountStanding releaseExpiredLock(AccountStanding standing, String label) {
        if (!standing.lockExpired(TenantTime.now(clock))) {
            return standing;
        }
        statusWriter.apply(standing.userId(), new UserStatusWriter.Change(UserStatus.ACTIVE, null, AUTO_UNLOCK, null, null), null);
        authCache.evictNow();
        recorder.recordIndependently(AuditEvent.of(AuditAction.USER_UNLOCKED, AuditTargets.USER, standing.userId(), label)
                .params(Map.of("detail", AUTO_UNLOCK))
                .build());
        events.publish(new IdentityEvents.AccountUnlocked(standing.userId(), AUTO_UNLOCK));
        return new AccountStanding(standing.userId(), UserStatus.ACTIVE, null, standing.accessExpiresAt(),
                standing.passwordNeedsChange(), standing.passwordChangedAt(), 0, standing.locale());
    }

    /** Clears the counter and stamps the login time. */
    public void registerSuccess(UUID userId) {
        credentials.recordSuccessfulLogin(userId, TenantTime.nowLocal(clock));
    }

    /** Whole seconds until {@code until} (never negative). */
    public long secondsUntil(Instant until) {
        return until == null ? 0 : Math.max(0, Duration.between(TenantTime.now(clock), until).toSeconds());
    }
}
