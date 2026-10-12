package com.ninsky.cronos.iam.signin;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.token.TokenCodec;
import com.ninsky.cronos.identity.domain.IdentityEvents;
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
 * The sign-in record (§2.3.3): one {@code login_history} row per attempt (unknown identifiers stored only as a SHA-256
 * hash) and the AUTHENTICATION audit events. Never stores secrets.
 * <p>
 * Off the request path (performance-latency P0-5): a failed attempt only stores an {@code identity.sign-in-failed}
 * outbox row in its own transaction (so it survives the caller's rollback), a successful one rides on
 * {@code identity.user-signed-in}; the consumers call {@link #record}, which is idempotent (the row id is the event id).
 */
@Slf4j
@Component
public class SignInJournal {

    private static final Set<SignInOutcome> WARNING = EnumSet.of(SignInOutcome.LOCKED, SignInOutcome.TWO_FACTOR_FAILED,
            SignInOutcome.RATE_LIMITED);

    private final NamedParameterJdbcTemplate jdbc;
    private final RequestContextUtil requestContext;
    private final AuditRecorder recorder;
    private final DomainEventPublisher events;
    private final TransactionTemplate independent;
    private final Clock clock;

    public SignInJournal(NamedParameterJdbcTemplate jdbc, RequestContextUtil requestContext, AuditRecorder recorder,
                         DomainEventPublisher events, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc;
        this.requestContext = requestContext;
        this.recorder = recorder;
        this.events = events;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Records one attempt that did not complete a sign-in (successful ones are recorded from
     * {@code identity.user-signed-in}).
     *
     * @param userId     null for an unknown identifier
     * @param identifier the normalised identifier typed (hashed before storage), null when unknown
     */
    public void attempt(UUID userId, String label, String identifier, SignInOutcome outcome, String failureReason, boolean twoFactorUsed) {
        attempt(userId, label, identifier, outcome, failureReason, twoFactorUsed, null);
    }

    /** Same as {@link #attempt(UUID, String, String, SignInOutcome, String, boolean)} with the failures counted so far. */
    public void attempt(UUID userId, String label, String identifier, SignInOutcome outcome, String failureReason, boolean twoFactorUsed,
                        Integer failedAttempts) {
        if (outcome.successful()) {
            return;
        }
        log.warn("Failed sign-in for user {}: {}", userId, outcome);
        IdentityEvents.SignInContext context = context(identifier, label);
        try {
            independent.executeWithoutResult(status -> events.publish(new IdentityEvents.SignInFailed(userId, outcome,
                    failureReason, twoFactorUsed, failedAttempts, context)));
        } catch (RuntimeException e) {
            log.error("Could not record the sign-in attempt: {}", e.getMessage());
        }
    }

    /** Request facts of the current attempt, for the events above. */
    public IdentityEvents.SignInContext context(String identifier, String label) {
        return new IdentityEvents.SignInContext(identifier == null ? null : TokenCodec.hash(identifier), label,
                cut(requestContext.getClientIp(), 45), cut(requestContext.getUserAgent(), 500), cut(requestContext.getBrowser(), 100),
                cut(requestContext.getOperatingSystem(), 100), cut(requestContext.getDevice(), 100), cut(requestContext.getLocation(), 100),
                cut(MDC.get(TraceContexts.MDC_TRACE_ID), 64), TenantTime.now(clock));
    }

    /**
     * Consumer side: writes the {@code login_history} row (id = {@code eventId}, so a redelivery inserts nothing) and the
     * audit entry, inside the consumer's transaction.
     */
    public void record(UUID eventId, UUID userId, SignInOutcome outcome, String failureReason, boolean twoFactorUsed,
                       IdentityEvents.SignInContext context) {
        IdentityEvents.SignInContext ctx = context != null ? context
                : new IdentityEvents.SignInContext(null, null, null, null, null, null, null, null, null, TenantTime.now(clock));
        int inserted = insert(eventId, userId, ctx, outcome, failureReason, twoFactorUsed);
        if (inserted == 0) {
            return;
        }
        AuditAction action = outcome.successful() ? AuditAction.LOGIN_SUCCEEDED
                : outcome == SignInOutcome.TWO_FACTOR_FAILED ? AuditAction.TWO_FACTOR_FAILED : AuditAction.LOGIN_FAILED;
        recorder.record(AuditEvent.of(action, AuditTargets.USER, userId, ctx.label())
                .outcome(outcome.successful() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE)
                .severity(outcome.successful() ? AuditSeverity.INFO : WARNING.contains(outcome) ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .params(outcome.successful() ? Map.of("twoFactor", twoFactorUsed) : Map.of("detail", outcome.name()))
                .build());
    }

    /** Sign-out of one session or of every session. */
    public void loggedOut(UUID userId, String label, boolean everywhere) {
        audit(AuditEvent.of(AuditAction.LOGOUT, AuditTargets.USER, userId, label)
                .severity(AuditSeverity.INFO)
                .params(Map.of("detail", everywhere ? "ALL_SESSIONS" : "SESSION")));
    }

    private int insert(UUID id, UUID userId, IdentityEvents.SignInContext ctx, SignInOutcome outcome, String failureReason,
                       boolean twoFactorUsed) {
        LocalDateTime at = TenantTime.toLocal(ctx.at());
        return jdbc.update("""
                        INSERT INTO login_history (id, user_id, status, outcome, successful, failure_reason, two_factor_used,
                                                   ip_address, user_agent, browser, operating_system, device, location,
                                                   login_at, created_at, identifier_hash, trace_id)
                        VALUES (:id, :userId, :status, :outcome, :successful, :failure, :twoFactor, :ip, :ua, :browser, :os, :device,
                                :location, :at, :at, :identifierHash, :traceId)
                        ON CONFLICT (id) DO NOTHING""",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", userId)
                        .addValue("status", outcome.successful() ? "SUCCESS" : "FAILED")
                        .addValue("outcome", outcome.name())
                        .addValue("successful", outcome.successful())
                        .addValue("failure", failureReason == null ? (outcome.successful() ? null : outcome.name()) : cut(failureReason, 255))
                        .addValue("twoFactor", twoFactorUsed)
                        .addValue("ip", ctx.ipAddress())
                        .addValue("ua", ctx.userAgent())
                        .addValue("browser", ctx.browser())
                        .addValue("os", ctx.operatingSystem())
                        .addValue("device", ctx.device())
                        .addValue("location", ctx.location())
                        .addValue("at", at)
                        .addValue("identifierHash", ctx.identifierHash())
                        .addValue("traceId", ctx.traceId()));
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
