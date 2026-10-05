package com.ninsky.cronos.iam.audit;

import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.infrastructure.web.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Appends to {@code audit_log} with JDBC inside the caller's transaction, chaining each row's hash
 * to the previous one under a transaction-scoped advisory lock (spec §7.4).
 */
@Component
public class JdbcAuditRecorder implements AuditRecorder {

    private static final long CHAIN_LOCK_KEY = 7_426_001L;
    private static final String UNKNOWN = "Unknown";

    private final NamedParameterJdbcTemplate jdbc;
    private final ActorProvider actors;
    private final RequestContextUtil requestContext;
    private final Clock clock;
    private final TransactionTemplate independent;

    public JdbcAuditRecorder(NamedParameterJdbcTemplate jdbc, ActorProvider actors, RequestContextUtil requestContext, Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
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

        jdbc.query("SELECT pg_advisory_xact_lock(:key)", Map.of("key", CHAIN_LOCK_KEY), rs -> { });
        List<String> previous = jdbc.queryForList(
                "SELECT hash FROM audit_log WHERE hash IS NOT NULL ORDER BY id DESC LIMIT 1", Map.of(), String.class);
        String prevHash = previous.isEmpty() ? null : previous.getFirst();
        String hash = AuditHasher.hash(prevHash, new AuditHasher.Material(createdAt, actor.map(Actor::id).orElse(null),
                event.action().name(), event.action().category().name(), event.outcome().name(), event.severity().name(),
                event.targetType(), event.targetId(), truncate(event.targetLabel(), 200), params, changes, truncate(event.reason(), 500)));

        jdbc.update("""
                INSERT INTO audit_log (actor_user_id, actor_username, actor_label, action, category, outcome, severity,
                                       target_type, target_id, target_label, params, changes, reason,
                                       ip_address, user_agent, trace_id, created_at, prev_hash, hash)
                VALUES (:actorId, :actorUsername, :actorLabel, :action, :category, :outcome, :severity,
                        :targetType, :targetId, :targetLabel, CAST(:params AS jsonb), CAST(:changes AS jsonb), :reason,
                        :ip, :userAgent, :traceId, :createdAt, :prevHash, :hash)""",
                new MapSqlParameterSource()
                        .addValue("actorId", actor.map(Actor::id).orElse(null))
                        .addValue("actorUsername", actor.map(Actor::username).orElse(null))
                        .addValue("actorLabel", truncate(actorLabel, 200))
                        .addValue("action", event.action().name())
                        .addValue("category", event.action().category().name())
                        .addValue("outcome", event.outcome().name())
                        .addValue("severity", event.severity().name())
                        .addValue("targetType", event.targetType())
                        .addValue("targetId", truncate(event.targetId(), 100))
                        .addValue("targetLabel", truncate(event.targetLabel(), 200))
                        .addValue("params", params)
                        .addValue("changes", changes)
                        .addValue("reason", truncate(event.reason(), 500))
                        .addValue("ip", known(truncate(requestContext.getClientIp(), 45)))
                        .addValue("userAgent", known(truncate(requestContext.getUserAgent(), 500)))
                        .addValue("traceId", truncate(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY), 64))
                        .addValue("createdAt", createdAt)
                        .addValue("prevHash", prevHash)
                        .addValue("hash", hash));
    }

    private String displayName(UUID userId, String fallback) {
        List<String> names = jdbc.queryForList("""
                SELECT NULLIF(btrim(concat_ws(' ', p.first_name, p.last_name)), '')
                FROM user_profiles p WHERE p.user_id = :id""", Map.of("id", userId), String.class);
        return names.isEmpty() || names.getFirst() == null ? fallback : names.getFirst();
    }

    private static String known(String value) {
        return value == null || UNKNOWN.equals(value) ? null : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
