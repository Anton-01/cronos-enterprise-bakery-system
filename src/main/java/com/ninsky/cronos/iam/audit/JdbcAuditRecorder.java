package com.ninsky.cronos.iam.audit;

import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Appends to {@code audit_log} with JDBC inside the caller's transaction, chaining each row's hash
 * to the previous one under a transaction-scoped advisory lock (spec §7.4).
 */
@Component
public class JdbcAuditRecorder implements AuditRecorder {

    private static final String UNKNOWN = "Unknown";

    private final AuditLogCustomRepository repository;
    private final ActorProvider actors;
    private final RequestContextUtil requestContext;
    private final Clock clock;
    private final TransactionTemplate independent;

    public JdbcAuditRecorder(AuditLogCustomRepository repository, ActorProvider actors, RequestContextUtil requestContext, Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.actors = actors;
        this.requestContext = requestContext;
        this.clock = clock;
        this.independent = new TransactionTemplate(transactionManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public void record(AuditEvent event) {
        write(event);
    }

    @Override
    public void recordIndependently(AuditEvent event) {
        independent.executeWithoutResult(status -> write(event));
    }

    private void write(AuditEvent event) {
        Optional<Actor> actor = actors.current();
        String actorLabel = actor.map(a -> displayName(a.id(), a.username())).orElse(null);
        LocalDateTime createdAt = TenantTime.nowLocal(clock);
        String params = event.params().isEmpty() ? null : AuditHasher.toJson(event.params());
        String changes = event.changes().isEmpty() ? null : AuditHasher.toJson(event.changes());

        repository.lockChain();
        String prevHash = repository.latestHash().orElse(null);
        String hash = AuditHasher.hash(prevHash, new AuditHasher.Material(createdAt, actor.map(Actor::id).orElse(null),
                event.action().name(), event.action().category().name(), event.outcome().name(), event.severity().name(),
                event.targetType(), event.targetId(), truncate(event.targetLabel(), 200), params, changes, truncate(event.reason(), 500)));

        repository.insert(new AuditLogCustomRepository.NewEntry(actor.map(Actor::id).orElse(null), actor.map(Actor::username).orElse(null),
                truncate(actorLabel, 200), event.action().name(), event.action().category().name(), event.outcome().name(),
                event.severity().name(), event.targetType(), truncate(event.targetId(), 100), truncate(event.targetLabel(), 200), params,
                changes, truncate(event.reason(), 500), known(truncate(requestContext.getClientIp(), 45)),
                known(truncate(requestContext.getUserAgent(), 500)), truncate(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY), 64), createdAt,
                prevHash, hash));
    }

    private String displayName(UUID userId, String fallback) {
        return repository.displayName(userId).orElse(fallback);
    }

    private static String known(String value) {
        return value == null || UNKNOWN.equals(value) ? null : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
