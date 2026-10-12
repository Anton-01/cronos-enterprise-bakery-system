package com.ninsky.cronos.calendar.infrastructure;

import com.ninsky.cronos.calendar.domain.CalendarEventStatus;
import com.ninsky.cronos.calendar.domain.ReminderKind;
import com.ninsky.cronos.calendar.domain.ReminderPlan;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** SQL of {@code calendar_reminders} (§5.6). */
@Repository
public class CalendarReminderCustomRepository {

    /** A due reminder with the facts of its entry. */
    public record DueReminder(UUID id, UUID eventId, ReminderKind kind, Integer minutesBefore, Instant fireAt, UUID ownerId, String title,
                              Instant startAt, boolean allDay, Instant productionStartAt, CalendarEventStatus status, boolean deleted) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public CalendarReminderCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Replaces the pending reminders of {@code eventId} with {@code planned}. One already sent for the same instant is
     * not inserted again (its dedupe key exists).
     */
    public void replacePending(UUID eventId, List<ReminderPlan.Planned> planned, Supplier<UUID> ids) {
        clearPending(eventId);
        if (planned.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = planned.stream().map(reminder -> new MapSqlParameterSource("id", ids.get())
                .addValue("event", eventId).addValue("kind", reminder.kind().name()).addValue("minutes", reminder.minutesBefore())
                .addValue("fireAt", Timestamp.from(reminder.fireAt())).addValue("dedupe", reminder.dedupeKey(eventId)))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO calendar_reminders (id, event_id, kind, minutes_before, fire_at, dedupe_key)
                VALUES (:id, :event, :kind, :minutes, :fireAt, :dedupe) ON CONFLICT (dedupe_key) DO NOTHING""", batch);
    }

    public void clearPending(UUID eventId) {
        jdbc.update("DELETE FROM calendar_reminders WHERE event_id = :event AND sent_at IS NULL", Map.of("event", eventId));
    }

    /** Claims due reminders (skipping rows another node holds), oldest first. */
    public List<DueReminder> claimDue(Instant now, int limit) {
        return jdbc.query("""
                        SELECT r.id, r.event_id, r.kind, r.minutes_before, r.fire_at, e.owner_id, e.title, e.start_at, e.all_day,
                               e.production_start_at, e.status, e.deleted_at IS NOT NULL AS deleted
                        FROM calendar_reminders r JOIN calendar_events e ON e.id = r.event_id
                        WHERE r.sent_at IS NULL AND r.fire_at <= :now
                        ORDER BY r.fire_at LIMIT :limit
                        FOR UPDATE OF r SKIP LOCKED""",
                new MapSqlParameterSource("now", Timestamp.from(now)).addValue("limit", limit),
                (rs, n) -> new DueReminder(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                        ReminderKind.valueOf(rs.getString("kind")), (Integer) rs.getObject("minutes_before"),
                        CalendarEventCustomRepository.instant(rs.getTimestamp("fire_at")), rs.getObject("owner_id", UUID.class),
                        rs.getString("title"), CalendarEventCustomRepository.instant(rs.getTimestamp("start_at")), rs.getBoolean("all_day"),
                        CalendarEventCustomRepository.instant(rs.getTimestamp("production_start_at")),
                        CalendarEventStatus.valueOf(rs.getString("status")), rs.getBoolean("deleted")));
    }

    public void markSent(Collection<UUID> ids, Instant now) {
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE calendar_reminders SET sent_at = :now WHERE id IN (:ids)",
                    new MapSqlParameterSource("ids", List.copyOf(ids)).addValue("now", Timestamp.from(now)));
        }
    }
}
