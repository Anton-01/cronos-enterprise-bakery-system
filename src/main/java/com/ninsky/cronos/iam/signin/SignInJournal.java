package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.domain.model.auth.LoginHistory;
import com.ninsky.cronos.domain.port.auth.LoginHistoryRepositoryPort;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Writes {@code login_history} rows and the AUTHENTICATION audit events of sign-in and sign-out.
 * Audit goes through {@code recordIndependently}, so it survives the caller's rollback; never secrets.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignInJournal {

    /** {@code login_history.outcome} values (spec §3.8). */
    public enum Outcome { SUCCESS, FAILURE, LOCKED, TWO_FACTOR_FAILED }

    /** Stored failure codes; the UI shows a generic localised text for all of them. */
    public enum Failure { UNKNOWN_ACCOUNT, INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_DISABLED, ACCESS_EXPIRED, INVALID_TWO_FACTOR_CODE }

    private final LoginHistoryRepositoryPort history;
    private final RequestContextUtil requestContext;
    private final AuditRecorder recorder;
    private final Clock clock;

    public void succeeded(UUID userId, String label, boolean twoFactorUsed) {
        save(userId, Outcome.SUCCESS, null, twoFactorUsed);
        recorder.recordIndependently(AuditEvent.of(AuditAction.LOGIN_SUCCEEDED, AuditTargets.USER, userId, label)
                .severity(AuditSeverity.INFO)
                .params(Map.of("twoFactor", twoFactorUsed))
                .build());
    }

    /** {@code userId} null for an unknown login id: audited without a target, no history row. */
    public void failed(UUID userId, String label, Outcome outcome, Failure failure) {
        log.warn("Failed sign-in for user {}: {}", userId, failure);
        if (userId != null) {
            save(userId, outcome, failure, outcome == Outcome.TWO_FACTOR_FAILED);
        }
        boolean twoFactor = outcome == Outcome.TWO_FACTOR_FAILED;
        audit(AuditEvent.of(twoFactor ? AuditAction.TWO_FACTOR_FAILED : AuditAction.LOGIN_FAILED, AuditTargets.USER, userId, label)
                .outcome(AuditOutcome.FAILURE)
                .severity(twoFactor || outcome == Outcome.LOCKED ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .params(Map.of("detail", failure.name())));
    }

    public void loggedOut(UUID userId, String label, boolean everywhere) {
        audit(AuditEvent.of(AuditAction.LOGOUT, AuditTargets.USER, userId, label)
                .severity(AuditSeverity.INFO)
                .params(Map.of("detail", everywhere ? "ALL_SESSIONS" : "SESSION")));
    }

    private void save(UUID userId, Outcome outcome, Failure failure, boolean twoFactorUsed) {
        LocalDateTime now = TenantTime.nowLocal(clock);
        history.save(LoginHistory.builder().userId(userId)
                .status(outcome == Outcome.SUCCESS ? "SUCCESS" : "FAILED")
                .outcome(outcome.name())
                .successful(outcome == Outcome.SUCCESS)
                .failureReason(failure == null ? null : failure.name())
                .twoFactorUsed(twoFactorUsed)
                .ipAddress(requestContext.getClientIp()).userAgent(requestContext.getUserAgent())
                .browser(requestContext.getBrowser()).operatingSystem(requestContext.getOperatingSystem())
                .device(requestContext.getDevice()).location(requestContext.getLocation())
                .loginAt(now).createdAt(now)
                .build());
    }

    private void audit(AuditEvent.AuditEventBuilder event) {
        try {
            recorder.recordIndependently(event.build());
        } catch (RuntimeException e) {
            log.error("Could not record sign-in audit event: {}", e.getMessage());
        }
    }
}
