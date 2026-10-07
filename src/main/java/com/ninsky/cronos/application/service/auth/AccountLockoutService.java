package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.policy.SecurityPolicy;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.signin.AccountStanding;
import com.ninsky.cronos.iam.signin.AuthProjectionCache;
import com.ninsky.cronos.iam.user.StatusReason;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.iam.user.UserStatusWriter;
import com.ninsky.cronos.iam.signin.CredentialCustomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Failed-attempt counting and automatic lockout driven by the security policy (spec §3.3, §8):
 * reaching {@code maxFailedAttempts} locks the account for {@code lockoutMinutes} through
 * {@link UserStatusWriter} and records a CRITICAL {@code USER_LOCKED}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockoutService {

    static final String AUTO_LOCKOUT = "AUTO_LOCKOUT";
    static final String AUTO_UNLOCK = "AUTO_UNLOCK";

    private final CredentialCustomRepository credentials;
    private final SecurityPolicyProvider policies;
    private final UserStatusWriter statusWriter;
    private final AuditRecorder recorder;
    private final AuthProjectionCache authCache;
    private final Clock clock;

    /** Counts one failure; returns true when this failure locked the account. */
    public boolean registerFailure(UUID userId, UserStatus status, String label) {
        Instant now = TenantTime.now(clock);
        Integer attempts = credentials.incrementFailedAttempts(userId, TenantTime.toLocal(now));
        SecurityPolicy policy = policies.current();
        if (status != UserStatus.ACTIVE || attempts == null || attempts < policy.maxFailedAttempts()) {
            return false;
        }
        Instant until = now.plus(Duration.ofMinutes(policy.lockoutMinutes()));
        statusWriter.apply(userId, new UserStatusWriter.Change(UserStatus.LOCKED, StatusReason.SECURITY_INCIDENT, AUTO_LOCKOUT,
                until, null), null);
        authCache.evictNow();
        recorder.recordIndependently(AuditEvent.of(AuditAction.USER_LOCKED, AuditTargets.USER, userId, label)
                .severity(AuditSeverity.CRITICAL)
                .params(Map.of("detail", attempts, "until", until.toString()))
                .build());
        log.warn("Account {} locked after {} failed attempts until {}", userId, attempts, until);
        return true;
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
        return new AccountStanding(standing.userId(), UserStatus.ACTIVE, null, standing.accessExpiresAt(),
                standing.passwordNeedsChange(), standing.passwordChangedAt(), 0, standing.locale());
    }

    /** Clears the counter and stamps the login time. */
    public void registerSuccess(UUID userId) {
        credentials.recordSuccessfulLogin(userId, TenantTime.nowLocal(clock));
    }

    public long getRemainingLockoutTime(Instant until) {
        return until == null ? 0 : Math.max(0, Duration.between(TenantTime.now(clock), until).toMinutes());
    }
}
