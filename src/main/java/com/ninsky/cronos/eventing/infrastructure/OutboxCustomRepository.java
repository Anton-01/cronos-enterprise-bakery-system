package com.ninsky.cronos.eventing.infrastructure;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL of the transactional outbox. Claiming uses {@code FOR UPDATE SKIP LOCKED} and a lease on
 * {@code available_at}, so any number of relays (threads or instances) can run without double dispatch, and a
 * message whose aggregate has an earlier pending message waits for it (per-aggregate ordering).
 */
@Repository
public class OutboxCustomRepository {

    static final RowMapper<OutboxMessage> ROW = OutboxCustomRepository::map;

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts {@code message}; false when its dedupe key already exists (coalesced). */
    public boolean insert(OutboxMessage message) {
        return jdbc.update("""
                INSERT INTO outbox_messages (id, type, aggregate_type, aggregate_id, payload, correlation_id, causation_id,
                                             trace_id, actor_id, occurred_at, available_at, status, attempts, dedupe_key)
                VALUES (:id, :type, :aggregateType, :aggregateId, CAST(:payload AS jsonb), :correlationId, :causationId,
                        :traceId, :actorId, :occurredAt, :availableAt, 'PENDING', 0, :dedupeKey)
                ON CONFLICT (dedupe_key) DO NOTHING""",
                new MapSqlParameterSource()
                        .addValue("id", message.id())
                        .addValue("type", message.type())
                        .addValue("aggregateType", message.aggregateType())
                        .addValue("aggregateId", message.aggregateId())
                        .addValue("payload", message.payload())
                        .addValue("correlationId", message.correlationId())
                        .addValue("causationId", message.causationId())
                        .addValue("traceId", message.traceId())
                        .addValue("actorId", message.actorId())
                        .addValue("occurredAt", Timestamp.from(message.occurredAt()))
                        .addValue("availableAt", Timestamp.from(message.availableAt()))
                        .addValue("dedupeKey", message.dedupeKey())) == 1;
    }

    /**
     * Claims up to {@code limit} due messages, oldest first, at most one per aggregate, by pushing their
     * {@code available_at} to {@code now + lease}. A relay that dies leaves them to be claimed again after the lease.
     */
    public List<OutboxMessage> claim(Instant now, Duration lease, int limit) {
        return jdbc.query("""
                        WITH due AS (
                            SELECT m.id FROM outbox_messages m
                            WHERE m.status = 'PENDING' AND m.available_at <= :now
                              AND NOT EXISTS (SELECT 1 FROM outbox_messages e
                                              WHERE e.aggregate_type = m.aggregate_type AND e.aggregate_id = m.aggregate_id
                                                AND e.status = 'PENDING' AND e.id < m.id)
                            ORDER BY m.id
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
                        UPDATE outbox_messages o SET available_at = :leaseUntil
                        FROM due WHERE o.id = due.id
                        RETURNING o.*""",
                new MapSqlParameterSource()
                        .addValue("now", Timestamp.from(now))
                        .addValue("leaseUntil", Timestamp.from(now.plus(lease)))
                        .addValue("limit", limit),
                ROW).stream().sorted(Comparator.comparing(OutboxMessage::id)).toList();
    }

    /** Every handler applied the message. */
    public void markDispatched(UUID id, Instant now) {
        jdbc.update("UPDATE outbox_messages SET status = 'DISPATCHED', dispatched_at = :now, last_error = NULL WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("now", Timestamp.from(now)));
    }

    /** Records a failed attempt; {@code dead} stops retries, otherwise the next attempt is at {@code nextAttemptAt}. */
    public void markFailed(UUID id, int attempts, Instant nextAttemptAt, String error, boolean dead) {
        jdbc.update("""
                        UPDATE outbox_messages
                        SET attempts = :attempts, available_at = :next, last_error = :error,
                            status = CASE WHEN :dead THEN 'DEAD' ELSE 'PENDING' END
                        WHERE id = :id""",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("attempts", attempts)
                        .addValue("next", Timestamp.from(nextAttemptAt))
                        .addValue("error", truncate(error, 1000))
                        .addValue("dead", dead));
    }

    /** Puts a DEAD message back in the queue with a fresh attempt budget; false when it is not DEAD. */
    public boolean requeueDead(UUID id, Instant now) {
        return jdbc.update("""
                        UPDATE outbox_messages SET status = 'PENDING', attempts = 0, available_at = :now, last_error = NULL
                        WHERE id = :id AND status = 'DEAD'""",
                new MapSqlParameterSource().addValue("id", id).addValue("now", Timestamp.from(now))) == 1;
    }

    /** One message by id. */
    public Optional<OutboxMessage> find(UUID id) {
        return jdbc.query("SELECT * FROM outbox_messages WHERE id = :id", new MapSqlParameterSource("id", id), ROW)
                .stream().findFirst();
    }

    /**
     * Marks {@code handler} as having applied {@code eventId}; false when it already had (redelivery).
     * Must run in the handler's transaction so the mark and the effect commit together.
     */
    public boolean markProcessed(String handler, UUID eventId, Instant now) {
        return jdbc.update("""
                        INSERT INTO processed_events (handler_name, event_id, processed_at) VALUES (:handler, :eventId, :now)
                        ON CONFLICT DO NOTHING""",
                new MapSqlParameterSource()
                        .addValue("handler", handler)
                        .addValue("eventId", eventId)
                        .addValue("now", Timestamp.from(now))) == 1;
    }

    /** Seconds the oldest due message has been waiting (0 when the queue is drained). */
    public double lagSeconds(Instant now) {
        Timestamp oldest = jdbc.queryForObject(
                "SELECT min(available_at) FROM outbox_messages WHERE status = 'PENDING' AND available_at <= :now",
                new MapSqlParameterSource("now", Timestamp.from(now)), Timestamp.class);
        return oldest == null ? 0 : Math.max(0, Duration.between(oldest.toInstant(), now).toMillis() / 1000.0);
    }

    /** Deletes dispatched messages (and their processed marks) older than {@code cutoff}; DEAD rows are kept. */
    public int purgeDispatchedBefore(Instant cutoff) {
        MapSqlParameterSource params = new MapSqlParameterSource("cutoff", Timestamp.from(cutoff));
        jdbc.update("""
                DELETE FROM processed_events p USING outbox_messages m
                WHERE p.event_id = m.id AND m.status = 'DISPATCHED' AND m.dispatched_at < :cutoff""", params);
        return jdbc.update("DELETE FROM outbox_messages WHERE status = 'DISPATCHED' AND dispatched_at < :cutoff", params);
    }

    static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    private static OutboxMessage map(ResultSet rs, int row) throws SQLException {
        return new OutboxMessage(
                rs.getObject("id", UUID.class),
                rs.getString("type"),
                rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("payload"),
                rs.getObject("correlation_id", UUID.class),
                rs.getObject("causation_id", UUID.class),
                rs.getString("trace_id"),
                rs.getObject("actor_id", UUID.class),
                instant(rs, "occurred_at"),
                instant(rs, "available_at"),
                OutboxStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getString("last_error"),
                instant(rs, "dispatched_at"),
                rs.getString("dedupe_key"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
