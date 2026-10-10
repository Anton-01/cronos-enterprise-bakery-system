package com.ninsky.cronos.eventing.infrastructure;

import com.ninsky.cronos.eventing.api.EventTrail;
import com.ninsky.cronos.eventing.api.EventTrailFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read side of the event log: {@code outbox_messages} + {@code processed_events} + delivery tables. */
@Repository
public class EventTrailCustomRepository {

    /** Handler marks of one event. */
    public record HandlerMark(UUID eventId, String handler, Instant processedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public EventTrailCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One page of messages matching {@code filter}, newest first. */
    public List<OutboxMessage> search(EventTrailFilter filter, int limit, long offset) {
        MapSqlParameterSource params = params(filter).addValue("limit", limit).addValue("offset", offset);
        return jdbc.query("SELECT * FROM outbox_messages" + where(filter) + " ORDER BY id DESC LIMIT :limit OFFSET :offset",
                params, OutboxCustomRepository.ROW);
    }

    /** Count of messages matching {@code filter}. */
    public long count(EventTrailFilter filter) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM outbox_messages" + where(filter), params(filter), Long.class);
        return total == null ? 0 : total;
    }

    /** {@code id} and its causes up to the root, root first. */
    public List<OutboxMessage> causeChain(UUID id) {
        List<OutboxMessage> chain = jdbc.query("""
                WITH RECURSIVE up AS (
                    SELECT m.*, 0 AS depth FROM outbox_messages m WHERE m.id = :id
                    UNION ALL
                    SELECT p.*, up.depth + 1 FROM outbox_messages p JOIN up ON p.id = up.causation_id WHERE up.depth < 50)
                SELECT * FROM up ORDER BY depth DESC""", new MapSqlParameterSource("id", id), OutboxCustomRepository.ROW);
        return List.copyOf(chain);
    }

    /** Every transitive effect of {@code id} (not including it), oldest first. */
    public List<OutboxMessage> effects(UUID id) {
        return jdbc.query("""
                WITH RECURSIVE down AS (
                    SELECT m.*, 1 AS depth FROM outbox_messages m WHERE m.causation_id = :id
                    UNION ALL
                    SELECT c.*, down.depth + 1 FROM outbox_messages c JOIN down ON c.causation_id = down.id WHERE down.depth < 50)
                SELECT * FROM down ORDER BY id""", new MapSqlParameterSource("id", id), OutboxCustomRepository.ROW);
    }

    /** Handler marks of {@code eventIds}, grouped by event. */
    public Map<UUID, List<HandlerMark>> handlers(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("""
                        SELECT event_id, handler_name, processed_at FROM processed_events
                        WHERE event_id IN (:ids) ORDER BY processed_at""",
                        new MapSqlParameterSource("ids", eventIds),
                        (rs, row) -> new HandlerMark(rs.getObject("event_id", UUID.class), rs.getString("handler_name"),
                                rs.getTimestamp("processed_at").toInstant()))
                .stream().collect(Collectors.groupingBy(HandlerMark::eventId, Collectors.toUnmodifiableList()));
    }

    /** Emails and pushes produced by {@code eventIds}. */
    public List<EventTrail.Delivery> deliveries(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        List<EventTrail.Delivery> deliveries = new ArrayList<>(jdbc.query("""
                        SELECT id, event_id, template_key, status, attempts, last_error, created_at, sent_at
                        FROM mail_messages WHERE event_id IN (:ids) ORDER BY created_at""",
                new MapSqlParameterSource("ids", eventIds),
                (rs, row) -> new EventTrail.Delivery(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class), "EMAIL",
                        rs.getString("template_key"), rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"),
                        OutboxCustomRepository.instant(rs, "created_at"), OutboxCustomRepository.instant(rs, "sent_at"))));
        deliveries.addAll(jdbc.query("""
                        SELECT d.id, d.event_id, coalesce(s.browser, '') || ' / ' || coalesce(s.os, '') AS detail, d.status,
                                   d.attempts, d.last_error, d.created_at, d.delivered_at
                            FROM push_deliveries d JOIN push_subscriptions s ON s.id = d.subscription_id
                            WHERE d.event_id IN (:ids) ORDER BY d.created_at""",
                    new MapSqlParameterSource("ids", eventIds),
                    (rs, row) -> new EventTrail.Delivery(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class), "PUSH",
                            rs.getString("detail"), rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"),
                            OutboxCustomRepository.instant(rs, "created_at"), OutboxCustomRepository.instant(rs, "delivered_at"))));
        return List.copyOf(deliveries);
    }

    private static String where(EventTrailFilter filter) {
        List<String> clauses = new ArrayList<>();
        if (filter.aggregateType() != null) {
            clauses.add("aggregate_type = :aggregateType");
        }
        if (filter.aggregateId() != null) {
            clauses.add("aggregate_id = :aggregateId");
        }
        if (filter.correlationId() != null) {
            clauses.add("correlation_id = :correlationId");
        }
        if (filter.type() != null) {
            clauses.add("type = :type");
        }
        if (filter.status() != null) {
            clauses.add("status = :status");
        }
        if (filter.from() != null) {
            clauses.add("occurred_at >= :from");
        }
        if (filter.to() != null) {
            clauses.add("occurred_at <= :to");
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private static MapSqlParameterSource params(EventTrailFilter filter) {
        return new MapSqlParameterSource()
                .addValue("aggregateType", filter.aggregateType())
                .addValue("aggregateId", filter.aggregateId())
                .addValue("correlationId", filter.correlationId())
                .addValue("type", filter.type())
                .addValue("status", filter.status())
                .addValue("from", filter.from() == null ? null : Timestamp.from(filter.from()))
                .addValue("to", filter.to() == null ? null : Timestamp.from(filter.to()));
    }
}
