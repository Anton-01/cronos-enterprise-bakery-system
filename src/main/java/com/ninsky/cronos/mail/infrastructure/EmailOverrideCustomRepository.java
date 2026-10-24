package com.ninsky.cronos.mail.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Administrators' content overrides ({@code email_template_override}) and their history. Writes are guarded by the
 * revision (optimistic locking): {@link #update} touches 0 rows when someone else saved first.
 */
@Repository
public class EmailOverrideCustomRepository {

    /** One override. */
    public record OverrideRow(UUID id, String templateKey, EmailLocale locale, LocalizedEmail content, long revision, Instant updatedAt,
                              UUID updatedBy) {
    }

    /** One history entry: {@code content} is null for a {@code RESET}. */
    public record HistoryRow(long revision, String action, LocalizedEmail content, Instant changedAt, UUID changedBy) {
    }

    private static final String COLUMNS = "id, template_key, locale, subject, preheader, blocks, revision, updated_at, updated_by";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectWriter blocksWriter;
    private final ObjectReader blocksReader;

    public EmailOverrideCustomRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        TypeReference<List<EmailBlock>> blocks = new TypeReference<>() {
        };
        this.blocksWriter = json.writerFor(blocks);
        this.blocksReader = json.readerFor(blocks);
    }

    /** Every override of the tenant (a handful: a few per template at most). */
    public List<OverrideRow> findAll(UUID tenant) {
        return jdbc.query("SELECT " + COLUMNS + " FROM email_template_override WHERE tenant_id = :tenant ORDER BY template_key, locale",
                Map.of("tenant", tenant), rowMapper());
    }

    public Optional<OverrideRow> find(UUID tenant, String key, EmailLocale locale) {
        return jdbc.query("SELECT " + COLUMNS + " FROM email_template_override WHERE tenant_id = :tenant AND template_key = :key AND locale = :locale",
                params(tenant, key, locale), rowMapper()).stream().findFirst();
    }

    /** Revision 1; false when an override already exists (a concurrent first save). */
    public boolean insert(UUID tenant, String key, EmailLocale locale, LocalizedEmail content, UUID actor, Instant now) {
        return jdbc.update("""
                INSERT INTO email_template_override (id, tenant_id, template_key, locale, subject, preheader, blocks, revision, updated_at, updated_by)
                VALUES (:id, :tenant, :key, :locale, :subject, :preheader, CAST(:blocks AS jsonb), 1, :now, :actor)
                ON CONFLICT (tenant_id, template_key, locale) DO NOTHING""",
                content(params(tenant, key, locale), content).addValue("id", UUID.randomUUID()).addValue("actor", actor)
                        .addValue("now", Timestamp.from(now))) == 1;
    }

    /** {@code UPDATE … WHERE revision = :expected}; false when the stored revision moved on (or the row is gone). */
    public boolean update(UUID tenant, String key, EmailLocale locale, long expected, LocalizedEmail content, UUID actor, Instant now) {
        return jdbc.update("""
                UPDATE email_template_override SET subject = :subject, preheader = :preheader, blocks = CAST(:blocks AS jsonb),
                    revision = revision + 1, updated_at = :now, updated_by = :actor
                WHERE tenant_id = :tenant AND template_key = :key AND locale = :locale AND revision = :expected""",
                content(params(tenant, key, locale), content).addValue("expected", expected).addValue("actor", actor)
                        .addValue("now", Timestamp.from(now))) == 1;
    }

    /** Deletes the override and returns it; empty when there was none. */
    public Optional<OverrideRow> delete(UUID tenant, String key, EmailLocale locale) {
        return jdbc.query("DELETE FROM email_template_override WHERE tenant_id = :tenant AND template_key = :key AND locale = :locale RETURNING "
                + COLUMNS, params(tenant, key, locale), rowMapper()).stream().findFirst();
    }

    /** Deletes the override only while it is still at {@code expected}; empty when it moved on or is gone. */
    public Optional<OverrideRow> delete(UUID tenant, String key, EmailLocale locale, long expected) {
        return jdbc.query("DELETE FROM email_template_override WHERE tenant_id = :tenant AND template_key = :key AND locale = :locale"
                + " AND revision = :expected RETURNING " + COLUMNS, params(tenant, key, locale).addValue("expected", expected), rowMapper())
                .stream().findFirst();
    }

    /** Appends a {@code SAVED} (with content) or {@code RESET} (content null) entry. */
    public void history(UUID tenant, String key, EmailLocale locale, long revision, String action, LocalizedEmail content, UUID actor,
                        Instant now, String traceId) {
        MapSqlParameterSource params = params(tenant, key, locale).addValue("id", UUID.randomUUID()).addValue("revision", revision)
                .addValue("action", action).addValue("actor", actor).addValue("now", Timestamp.from(now)).addValue("trace", traceId);
        if (content == null) {
            params.addValue("subject", null).addValue("preheader", null).addValue("blocks", null);
        } else {
            content(params, content);
        }
        jdbc.update("""
                INSERT INTO email_template_override_history (id, tenant_id, template_key, locale, revision, action, subject, preheader, blocks,
                                                             changed_at, changed_by, trace_id)
                VALUES (:id, :tenant, :key, :locale, :revision, :action, :subject, :preheader, CAST(:blocks AS jsonb), :now, :actor, :trace)""",
                params);
    }

    /** History of one {@code (key, locale)}, newest first. */
    public CatalogPage<HistoryRow> historyPage(UUID tenant, String key, EmailLocale locale, int page, int size) {
        MapSqlParameterSource params = params(tenant, key, locale).addValue("limit", size).addValue("offset", (long) page * size);
        long total = Optional.ofNullable(jdbc.queryForObject("""
                SELECT count(*) FROM email_template_override_history WHERE tenant_id = :tenant AND template_key = :key AND locale = :locale""",
                params, Long.class)).orElse(0L);
        List<HistoryRow> rows = jdbc.query("""
                SELECT revision, action, subject, preheader, blocks, changed_at, changed_by FROM email_template_override_history
                WHERE tenant_id = :tenant AND template_key = :key AND locale = :locale
                ORDER BY changed_at DESC, revision DESC LIMIT :limit OFFSET :offset""", params,
                (rs, n) -> new HistoryRow(rs.getLong("revision"), rs.getString("action"),
                        rs.getString("subject") == null ? null : content(rs), rs.getTimestamp("changed_at").toInstant(),
                        rs.getObject("changed_by", UUID.class)));
        int totalPages = size == 0 ? 0 : (int) ((total + size - 1) / size);
        return new CatalogPage<>(rows, page, size, total, totalPages, page >= totalPages - 1);
    }

    /** Display names ({@code first last}, else the username) of {@code userIds}. */
    public Map<UUID, String> displayNames(Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        jdbc.query("""
                SELECT u.id, coalesce(NULLIF(btrim(concat_ws(' ', p.first_name, p.last_name)), ''), u.username) AS name
                FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id WHERE u.id IN (:ids)""",
                Map.of("ids", List.copyOf(userIds)),
                (RowCallbackHandler) rs -> names.put(rs.getObject("id", UUID.class), rs.getString("name")));
        return names;
    }

    private RowMapper<OverrideRow> rowMapper() {
        return (rs, n) -> new OverrideRow(rs.getObject("id", UUID.class), rs.getString("template_key"),
                EmailLocale.fromTag(rs.getString("locale")).orElseThrow(), content(rs), rs.getLong("revision"),
                rs.getTimestamp("updated_at").toInstant(), rs.getObject("updated_by", UUID.class));
    }

    private LocalizedEmail content(ResultSet rs) throws SQLException {
        try {
            return new LocalizedEmail(rs.getString("subject"), rs.getString("preheader"), blocksReader.readValue(rs.getString("blocks")));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored email blocks cannot be read", e);
        }
    }

    private MapSqlParameterSource content(MapSqlParameterSource params, LocalizedEmail content) {
        try {
            return params.addValue("subject", content.subject()).addValue("preheader", content.preheader())
                    .addValue("blocks", blocksWriter.writeValueAsString(content.blocks()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Email blocks cannot be written", e);
        }
    }

    private static MapSqlParameterSource params(UUID tenant, String key, EmailLocale locale) {
        return new MapSqlParameterSource().addValue("tenant", tenant).addValue("key", key).addValue("locale", locale.tag());
    }
}
