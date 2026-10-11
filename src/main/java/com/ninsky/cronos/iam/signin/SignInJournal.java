package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.token.TokenCodec;
import com.ninsky.cronos.identity.domain.SignInOutcome;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writes one {@code login_history} row per sign-in attempt (§2.3.3) — also for unknown identifiers, which are only
 * stored as a SHA-256 hash — and the AUTHENTICATION audit events. Both survive the caller's rollback (own
 * transaction), so a rejected sign-in is always on record. Never stores secrets.
 */
@Slf4j
@Component
public class SignInJournal {

    private static final Set<SignInOutcome> WARNING = EnumSet.of(SignInOutcome.LOCKED, SignInOutcome.TWO_FACTOR_FAILED,
            SignInOutcome.RATE_LIMITED);

    private final NamedParameterJdbcTemplate jdbc;
    private final RequestContextUtil requestContext;
    private final AuditRecorder recorder;
    private final TransactionTemplate independent;
    private final Clock clock;

    public SignInJournal(NamedParameterJdbcTemplate jdbc, RequestContextUtil requestContext, AuditRecorder recorder,
                         PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc;
        this.requestContext = requestContext;
        this.recorder = recorder;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Records one attempt.
     *
     * @param userId     null for an unknown identifier
     * @param identifier the normalised identifier typed (hashed before storage), null when unknown
     */
    public void attempt(UUID userId, String label, String identifier, SignInOutcome outcome, String failureReason, boolean twoFactorUsed) {
        if (!outcome.successful()) {
            log.warn("Failed sign-in for user {}: {}", userId, outcome);
        }
        try {
            independent.executeWithoutResult(status -> insert(userId, identifier, outcome, failureReason, twoFactorUsed));
        } catch (RuntimeException e) {
            log.error("Could not record the sign-in attempt: {}", e.getMessage());
        }
        AuditAction action = outcome.successful() ? AuditAction.LOGIN_SUCCEEDED
                : outcome == SignInOutcome.TWO_FACTOR_FAILED ? AuditAction.TWO_FACTOR_FAILED : AuditAction.LOGIN_FAILED;
        audit(AuditEvent.of(action, AuditTargets.USER, userId, label)
                .outcome(outcome.successful() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE)
                .severity(outcome.successful() ? AuditSeverity.INFO : WARNING.contains(outcome) ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .params(outcome.successful() ? Map.of("twoFactor", twoFactorUsed) : Map.of("detail", outcome.name())));
    }

    /** Sign-out of one session or of every session. */
    public void loggedOut(UUID userId, String label, boolean everywhere) {
        audit(AuditEvent.of(AuditAction.LOGOUT, AuditTargets.USER, userId, label)
                .severity(AuditSeverity.INFO)
                .params(Map.of("detail", everywhere ? "ALL_SESSIONS" : "SESSION")));
    }

    private void insert(UUID userId, String identifier, SignInOutcome outcome, String failureReason, boolean twoFactorUsed) {
        LocalDateTime now = TenantTime.nowLocal(clock);
        jdbc.update("""
                        INSERT INTO login_history (id, user_id, status, outcome, successful, failure_reason, two_factor_used,
                                                   ip_address, user_agent, browser, operating_system, device, location,
                                                   login_at, created_at, identifier_hash, trace_id)
                        VALUES (:id, :userId, :status, :outcome, :successful, :failure, :twoFactor, :ip, :ua, :browser, :os, :device,
                                :location, :now, :now, :identifierHash, :traceId)""",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("userId", userId)
                        .addValue("status", outcome.successful() ? "SUCCESS" : "FAILED")
                        .addValue("outcome", outcome.name())
                        .addValue("successful", outcome.successful())
                        .addValue("failure", failureReason == null ? (outcome.successful() ? null : outcome.name()) : cut(failureReason, 255))
                        .addValue("twoFactor", twoFactorUsed)
                        .addValue("ip", cut(requestContext.getClientIp(), 45))
                        .addValue("ua", cut(requestContext.getUserAgent(), 500))
                        .addValue("browser", cut(requestContext.getBrowser(), 100))
                        .addValue("os", cut(requestContext.getOperatingSystem(), 100))
                        .addValue("device", cut(requestContext.getDevice(), 100))
                        .addValue("location", cut(requestContext.getLocation(), 100))
                        .addValue("now", now)
                        .addValue("identifierHash", identifier == null ? null : TokenCodec.hash(identifier))
                        .addValue("traceId", cut(MDC.get(TraceContexts.MDC_TRACE_ID), 64)));
    }

    private void audit(AuditEvent.AuditEventBuilder event) {
        try {
            recorder.recordIndependently(event.build());
        } catch (RuntimeException e) {
            log.error("Could not record sign-in audit event: {}", e.getMessage());
        }
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
