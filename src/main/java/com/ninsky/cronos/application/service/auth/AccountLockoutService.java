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
import com.ninsky.cronos.identity.infrastructure.LoginAttemptStore;
import com.ninsky.cronos.identity.infrastructure.SignInPolicyProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Progressive account lockout (§2.3.1). Failures are counted in a sliding window of {@code failureWindowMinutes};
 * reaching {@code maxFailedAttempts} locks the account for {@code lockoutMinutes × multiplier^(lockCount-1)}, capped by
 * {@code lockoutMaxMinutes}; the lock count decays after {@code lockMemoryHours} without locks.
 * <p>
 * Counters and the lock window live in Redis ({@link LoginAttemptStore}), so a wrong password costs no PostgreSQL write
 * on the request path (performance-latency P0-5). Locking publishes {@code identity.account-locked} in its own
 * transaction; its consumer ({@link #applyLock}) writes the durable state ({@code users.status = LOCKED},
 * {@code lock_count}), the audit entry and, through the mail handler, the email. An expired lock publishes
 * {@code identity.account-unlocked}.
 */
@Slf4j
@Service
public class AccountLockoutService {

    static final String AUTO_LOCKOUT = "AUTO_LOCKOUT";
    static final String AUTO_UNLOCK = "AUTO_UNLOCK";

    /**
     * Outcome of one failure: whether it locked the account, attempts left before a lock, the lock end and the failures
     * counted in the current window.
     */
    public record FailureResult(boolean locked, int remainingAttempts, Instant lockedUntil, int attempts) {
    }

    private final CredentialCustomRepository credentials;
    private final SignInPolicyProvider policies;
    private final UserStatusWriter statusWriter;
    private final AuditRecorder recorder;
    private final AuthProjectionCache authCache;
    private final DomainEventPublisher events;
    private final LoginAttemptStore attempts;
    private final TransactionTemplate transaction;
    private final TransactionTemplate independent;
    private final Clock clock;

    public AccountLockoutService(CredentialCustomRepository credentials, SignInPolicyProvider policies, UserStatusWriter statusWriter,
                                 AuditRecorder recorder, AuthProjectionCache authCache, DomainEventPublisher events,
                                 LoginAttemptStore attempts, PlatformTransactionManager transactions, Clock clock) {
        this.credentials = credentials;
        this.policies = policies;
        this.statusWriter = statusWriter;
        this.recorder = recorder;
        this.authCache = authCache;
        this.events = events;
        this.attempts = attempts;
        this.transaction = new TransactionTemplate(transactions);
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Counts one failed sign-in (or screen unlock) in Redis. Only ACTIVE accounts are locked; the lock event is stored
     * in its own transaction so it survives the caller's rollback.
     */
    public FailureResult registerFailure(UUID userId, UserStatus status, String label) {
        Instant now = TenantTime.now(clock);
        SignInPolicy policy = policies.current();
        int failures = attempts.registerFailure(userId, policy.failureWindow());
        int remaining = Math.max(0, policy.maxFailedAttempts() - failures);
        if (status != UserStatus.ACTIVE || remaining > 0) {
            return new FailureResult(false, remaining, null, failures);
        }
        int lockCount = attempts.nextLockCount(userId, policy.lockMemory());
        Instant until = now.plus(policy.schedule().durationFor(lockCount));
        attempts.lock(userId, now, until);
        independent.executeWithoutResult(tx -> events.publish(new IdentityEvents.AccountLocked(userId, until, lockCount, failures)));
        log.warn("Account {} ({}) locked (lock #{}) after {} failed attempts until {}", userId, label, lockCount, failures, until);
        return new FailureResult(true, 0, until, failures);
    }

    /**
     * Consumer side of {@code identity.account-locked}: the durable lock (status, lock count) and the audit entry. The
     * status writer is idempotent for an account already LOCKED until the same instant.
     */
    public void applyLock(IdentityEvents.AccountLocked event, String label) {
        statusWriter.apply(event.userId(), new UserStatusWriter.Change(UserStatus.LOCKED, StatusReason.SECURITY_INCIDENT, AUTO_LOCKOUT,
                event.lockedUntil(), null), null);
        credentials.recordLock(event.userId(), event.lockCount(), TenantTime.now(clock));
        authCache.evictAfterCommit();
        recorder.record(AuditEvent.of(AuditAction.USER_LOCKED, AuditTargets.USER, event.userId(), label)
                .severity(AuditSeverity.CRITICAL)
                .params(Map.of("detail", event.failedAttempts(), "until", event.lockedUntil().toString(), "lockCount", event.lockCount()))
                .build());
    }

    /** Re-activates an account whose automatic lock already expired; returns the refreshed standing. */
    public AccountStanding releaseExpiredLock(AccountStanding standing, String label) {
        if (!standing.lockExpired(TenantTime.now(clock))) {
            return standing;
        }
        transaction.executeWithoutResult(tx -> {
            statusWriter.apply(standing.userId(), new UserStatusWriter.Change(UserStatus.ACTIVE, null, AUTO_UNLOCK, null, null), null);
            events.publish(new IdentityEvents.AccountUnlocked(standing.userId(), AUTO_UNLOCK));
        });
        authCache.evictNow();
        recorder.recordIndependently(AuditEvent.of(AuditAction.USER_UNLOCKED, AuditTargets.USER, standing.userId(), label)
                .params(Map.of("detail", AUTO_UNLOCK))
                .build());
        return new AccountStanding(standing.userId(), UserStatus.ACTIVE, null, standing.accessExpiresAt(),
                standing.passwordNeedsChange(), standing.passwordChangedAt(), 0, standing.locale());
    }

    /** End of a lock still only known to Redis (the durable status follows within milliseconds). */
    public Optional<Instant> lockedUntil(UUID userId) {
        return attempts.lockedUntil(userId, TenantTime.now(clock));
    }

    /**
     * Clears the failure window. {@code last_login_at} and the durable counter are reset by the
     * {@code identity.user-signed-in} consumer.
     */
    public void registerSuccess(UUID userId) {
        attempts.clearFailures(userId);
    }

    /** Whole seconds until {@code until} (never negative). */
    public long secondsUntil(Instant until) {
        return until == null ? 0 : Math.max(0, Duration.between(TenantTime.now(clock), until).toSeconds());
    }
}
