package com.ninsky.cronos.iam.audit.query;

import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Filtered, newest-first reads of the {@code audit_log} ledger (spec §7.3). */
@Repository
@RequiredArgsConstructor
public class AuditEventRepository {

    private static final int FETCH_SIZE = 500;
    private static final String SELECT = """
            SELECT a.id, a.created_at, a.category, a.action, a.outcome, a.severity,
                   a.actor_user_id, a.actor_username, a.actor_label, au.avatar_key AS actor_avatar_key,
                   a.target_type, a.target_id, coalesce(a.target_label, tu.username) AS target_label,
                   a.params::text AS params, a.changes::text AS changes, a.reason, a.ip_address, a.user_agent, a.trace_id
            FROM audit_log a
            LEFT JOIN users au ON au.id = a.actor_user_id
            LEFT JOIN users tu ON a.target_type = 'USER' AND a.target_label IS NULL AND tu.id::text = a.target_id
            """;
    private static final String ORDER = " ORDER BY a.created_at DESC, a.id DESC";

    private static final RowMapper<AuditRow> ROWS = (rs, i) -> new AuditRow(rs.getLong("id"),
            rs.getObject("created_at", LocalDateTime.class), rs.getString("category"), rs.getString("action"),
            rs.getString("outcome"), rs.getString("severity"), rs.getObject("actor_user_id", UUID.class),
            rs.getString("actor_username"), rs.getString("actor_label"), rs.getString("actor_avatar_key"),
            rs.getString("target_type"), rs.getString("target_id"), rs.getString("target_label"), rs.getString("params"),
            rs.getString("changes"), rs.getString("reason"), rs.getString("ip_address"), rs.getString("user_agent"),
            rs.getString("trace_id"));

    private final NamedParameterJdbcTemplate jdbc;

    public List<AuditRow> page(AuditEventFilter filter, long offset, int limit) {
        Where where = where(filter);
        return jdbc.query(SELECT + where.sql() + ORDER + " LIMIT :limit OFFSET :offset",
                where.params().addValue("limit", limit).addValue("offset", offset), ROWS);
    }

    public long count(AuditEventFilter filter) {
        Where where = where(filter);
        Long count = jdbc.queryForObject("SELECT count(*) FROM audit_log a" + where.sql(), where.params(), Long.class);
        return count == null ? 0 : count;
    }

    /** Streams up to {@code limit} rows with a server-side cursor; call inside a transaction. */
    public void stream(AuditEventFilter filter, int limit, Consumer<AuditRow> sink) {
        Where where = where(filter);
        JdbcTemplate template = new JdbcTemplate(jdbc.getJdbcTemplate().getDataSource());
        template.setFetchSize(FETCH_SIZE);
        new NamedParameterJdbcTemplate(template).query(SELECT + where.sql() + ORDER + " LIMIT :limit",
                where.params().addValue("limit", limit), (RowCallbackHandler) rs -> sink.accept(ROWS.mapRow(rs, 0)));
    }

    private record Where(String sql, MapSqlParameterSource params) {
    }

    private static Where where(AuditEventFilter f) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (f.search() != null) {
            clauses.add("""
                    (a.action ILIKE :search OR a.actor_username ILIKE :search OR a.trace_id ILIKE :search
                     OR a.ip_address ILIKE :search OR unaccent(coalesce(a.actor_label, '')) ILIKE unaccent(:search)
                     OR unaccent(coalesce(a.target_label, '')) ILIKE unaccent(:search))""");
            params.addValue("search", "%" + likeEscape(f.search()) + "%");
        }
        addIn(clauses, params, "a.category", "categories", names(f.categories()));
        addIn(clauses, params, "a.outcome", "outcomes", names(f.outcomes()));
        addIn(clauses, params, "a.severity", "severities", names(f.severities()));
        if (f.actorId() != null) {
            clauses.add("a.actor_user_id = :actorId");
            params.addValue("actorId", f.actorId());
        }
        if (f.targetType() != null) {
            clauses.add("a.target_type = :targetType");
            params.addValue("targetType", f.targetType());
        }
        if (f.targetId() != null) {
            clauses.add("a.target_id = :targetId");
            params.addValue("targetId", f.targetId());
        }
        if (f.from() != null) {
            clauses.add("a.created_at >= :from");
            params.addValue("from", TenantTime.toLocal(f.from()));
        }
        if (f.to() != null) {
            clauses.add("a.created_at <= :to");
            params.addValue("to", TenantTime.toLocal(f.to()));
        }
        return new Where(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), params);
    }

    private static void addIn(List<String> clauses, MapSqlParameterSource params, String column, String name, List<String> values) {
        if (!values.isEmpty()) {
            clauses.add(column + " IN (:" + name + ")");
            params.addValue(name, values);
        }
    }

    private static List<String> names(List<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).distinct().toList();
    }

    static String likeEscape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
