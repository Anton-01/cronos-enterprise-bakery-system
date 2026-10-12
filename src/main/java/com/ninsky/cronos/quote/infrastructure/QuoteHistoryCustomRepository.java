package com.ninsky.cronos.quote.infrastructure;

import com.ninsky.cronos.application.response.quote.QuoteStatusChange;
import com.ninsky.cronos.domain.entity.enums.QuoteStatus;
import com.ninsky.cronos.quote.domain.QuoteActorType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code quote_status_history}: one row per transition (and per out-of-band share), written in the same transaction. */
@Repository
public class QuoteHistoryCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public QuoteHistoryCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Appends an entry. */
    public void append(UUID quoteId, QuoteStatus status, QuoteActorType actorType, UUID actorId, String actorName, String detail,
                       String comment, Instant at, String traceId) {
        jdbc.update("""
                        INSERT INTO quote_status_history (id, quote_id, status, actor_type, actor_id, actor_name, detail, comment, changed_at, trace_id)
                        VALUES (:id, :quoteId, :status, :actorType, :actorId, :actorName, :detail, :comment, :at, :traceId)""",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("quoteId", quoteId)
                        .addValue("status", status.name())
                        .addValue("actorType", actorType.name())
                        .addValue("actorId", actorId)
                        .addValue("actorName", cut(actorName, 150))
                        .addValue("detail", cut(detail, 64))
                        .addValue("comment", cut(comment, 500))
                        .addValue("at", Timestamp.from(at))
                        .addValue("traceId", cut(traceId, 64)));
    }

    /** The history of a quote, oldest first. */
    public List<QuoteStatusChange> history(UUID quoteId) {
        return jdbc.query("""
                        SELECT status, changed_at, actor_type, actor_name, detail, comment FROM quote_status_history
                        WHERE quote_id = :id ORDER BY changed_at, id""", Map.of("id", quoteId),
                (rs, row) -> new QuoteStatusChange(rs.getString("status"), rs.getTimestamp("changed_at").toInstant(),
                        rs.getString("actor_type"), rs.getString("actor_name"), rs.getString("detail"), rs.getString("comment")));
    }

    /** True when the quote already has a share entry for {@code channel} on {@code day} (tenant calendar). */
    public boolean sharedOn(UUID quoteId, String channel, LocalDate day, ZoneId zone) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM quote_status_history
                                       WHERE quote_id = :id AND detail = :channel AND changed_at >= :from AND changed_at < :to)""",
                new MapSqlParameterSource("id", quoteId).addValue("channel", channel)
                        .addValue("from", Timestamp.from(day.atStartOfDay(zone).toInstant()))
                        .addValue("to", Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant())), Boolean.class));
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
