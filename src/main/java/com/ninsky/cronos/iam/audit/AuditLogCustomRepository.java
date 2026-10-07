package com.ninsky.cronos.iam.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** {@code audit_log} SQL: the chained append, the chain walk and dedupe checks. */
@Repository
public class AuditLogCustomRepository {

    private static final long CHAIN_LOCK_KEY = 7_426_001L;
    private static final int FETCH_SIZE = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate streaming;

    public AuditLogCustomRepository(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.streaming = new JdbcTemplate(dataSource);
        this.streaming.setFetchSize(FETCH_SIZE);
    }

    /** Values of one ledger row, already truncated. */
    public record NewEntry(UUID actorId, String actorUsername, String actorLabel, String action, String category, String outcome,
                           String severity, String targetType, String targetId, String targetLabel, String params, String changes,
                           String reason, String ip, String userAgent, String traceId, LocalDateTime createdAt, String prevHash,
                           String hash) {
    }

    /** A hashed row as the verifier recomputes it. */
    public record ChainRow(long id, String prevHash, String hash, AuditHasher.Material material) {
    }

    /** Serialises chain appends until the transaction ends. */
    public void lockChain() {
        jdbc.query("SELECT pg_advisory_xact_lock(:key)", Map.of("key", CHAIN_LOCK_KEY), rs -> { });
    }

    public Optional<String> latestHash() {
        return jdbc.queryForList("SELECT hash FROM audit_log WHERE hash IS NOT NULL ORDER BY id DESC LIMIT 1", Map.of(), String.class)
                .stream().findFirst();
    }

    public void insert(NewEntry e) {
        jdbc.update("""
                INSERT INTO audit_log (actor_user_id, actor_username, actor_label, action, category, outcome, severity,
                                       target_type, target_id, target_label, params, changes, reason,
                                       ip_address, user_agent, trace_id, created_at, prev_hash, hash)
                VALUES (:actorId, :actorUsername, :actorLabel, :action, :category, :outcome, :severity,
                        :targetType, :targetId, :targetLabel, CAST(:params AS jsonb), CAST(:changes AS jsonb), :reason,
                        :ip, :userAgent, :traceId, :createdAt, :prevHash, :hash)""",
                new MapSqlParameterSource()
                        .addValue("actorId", e.actorId()).addValue("actorUsername", e.actorUsername()).addValue("actorLabel", e.actorLabel())
                        .addValue("action", e.action()).addValue("category", e.category()).addValue("outcome", e.outcome())
                        .addValue("severity", e.severity()).addValue("targetType", e.targetType()).addValue("targetId", e.targetId())
                        .addValue("targetLabel", e.targetLabel()).addValue("params", e.params()).addValue("changes", e.changes())
                        .addValue("reason", e.reason()).addValue("ip", e.ip()).addValue("userAgent", e.userAgent())
                        .addValue("traceId", e.traceId()).addValue("createdAt", e.createdAt()).addValue("prevHash", e.prevHash())
                        .addValue("hash", e.hash()));
    }

    /** Profile display name ("first last"), empty when blank or missing. */
    public Optional<String> displayName(UUID userId) {
        List<String> names = jdbc.queryForList("""
                SELECT NULLIF(btrim(concat_ws(' ', p.first_name, p.last_name)), '')
                FROM user_profiles p WHERE p.user_id = :id""", Map.of("id", userId), String.class);
        return names.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /** Streams every hashed row in id order (caller supplies the transaction). */
    public void forEachChainRow(Consumer<ChainRow> consumer) {
        streaming.query("""
                SELECT id, created_at, actor_user_id, action, category, outcome, severity, target_type, target_id,
                       target_label, params::text AS params, changes::text AS changes, reason, prev_hash, hash
                FROM audit_log WHERE hash IS NOT NULL ORDER BY id""", (RowCallbackHandler) rs -> consumer.accept(new ChainRow(
                rs.getLong("id"), rs.getString("prev_hash"), rs.getString("hash"),
                new AuditHasher.Material(rs.getObject("created_at", LocalDateTime.class), rs.getObject("actor_user_id", UUID.class),
                        rs.getString("action"), rs.getString("category"), rs.getString("outcome"), rs.getString("severity"),
                        rs.getString("target_type"), rs.getString("target_id"), rs.getString("target_label"), rs.getString("params"),
                        rs.getString("changes"), rs.getString("reason")))));
    }

    /** Rows of {@code action} in [from, to) — created_at holds tenant wall time. */
    public boolean existsBetween(String action, LocalDateTime from, LocalDateTime to) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM audit_log WHERE action = :action AND created_at >= :from AND created_at < :to)""",
                new MapSqlParameterSource().addValue("action", action).addValue("from", from).addValue("to", to), Boolean.class));
    }

    /** A row of {@code action} on the target since {@code from}. */
    public boolean existsForTargetSince(String action, String targetType, String targetId, LocalDateTime from) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM audit_log WHERE action = :action AND target_type = :type AND target_id = :id
                               AND created_at >= :from)""",
                new MapSqlParameterSource().addValue("action", action).addValue("type", targetType).addValue("id", targetId)
                        .addValue("from", from), Boolean.class));
    }
}
