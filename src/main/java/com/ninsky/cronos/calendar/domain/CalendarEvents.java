package com.ninsky.cronos.calendar.domain;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;
import com.ninsky.cronos.eventing.domain.Sensitive;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain events of the orders calendar (§5.4, §8.2). Aggregate: the calendar entry, so every event of one entry is
 * dispatched in order (the reminder projection relies on it). Payloads carry ids and the facts consumers need; client
 * data is read back by the consumer.
 */
public final class CalendarEvents {

    public static final String CALENDAR_EVENT = "CALENDAR_EVENT";

    private CalendarEvents() {
    }

    /** One changed field of an update; client fields are part of a {@link Sensitive} map. */
    public record Change(String from, String to) {
    }

    @EventType("calendar.event.created")
    public record CalendarEventCreated(UUID eventId, UUID ownerId, CalendarEventType type, CalendarEventStatus status, Instant startAt,
                                       Instant endAt, UUID quoteId) implements DomainEvent {
        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }

    /** Fields edited through {@code PUT}; {@code changes} maps the field to its old and new value. */
    @EventType("calendar.event.updated")
    public record CalendarEventUpdated(UUID eventId, UUID ownerId, @Sensitive Map<String, Change> changes) implements DomainEvent {
        public CalendarEventUpdated {
            changes = Map.copyOf(changes);
        }

        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }

    /** Moved in time (drag-to-reschedule or an edit of the dates). */
    @EventType("calendar.event.rescheduled")
    public record CalendarEventRescheduled(UUID eventId, UUID ownerId, Instant previousStartAt, Instant previousEndAt, Instant startAt,
                                           Instant endAt) implements DomainEvent {
        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }

    @EventType("calendar.event.status-changed")
    public record CalendarEventStatusChanged(UUID eventId, UUID ownerId, CalendarEventStatus from, CalendarEventStatus to, String reason)
            implements DomainEvent {
        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }

    /** Also published (after {@link CalendarEventStatusChanged}) when the status became CANCELLED. */
    @EventType("calendar.event.cancelled")
    public record CalendarEventCancelled(UUID eventId, UUID ownerId, UUID quoteId, String reason) implements DomainEvent {
        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }

    /** Soft-deleted; a linked quote drops its {@code calendar_event_id}. */
    @EventType("calendar.event.deleted")
    public record CalendarEventDeleted(UUID eventId, UUID ownerId, UUID quoteId) implements DomainEvent {
        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }

    /** An accepted quote was scheduled as an order (the client is told when the quote has an email). */
    @EventType("calendar.order-scheduled")
    public record OrderScheduled(UUID eventId, UUID ownerId, UUID quoteId, String quoteNumber, CalendarEventType type, Instant startAt,
                                 boolean allDay) implements DomainEvent {
        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }

    /** A reminder before the start is due. */
    @EventType("calendar.reminder-due")
    public record CalendarReminderDue(UUID eventId, UUID ownerId, UUID reminderId, int minutesBefore, String title, Instant startAt,
                                      boolean allDay) implements DomainEvent {
        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }

    /** Production of an order should start now. */
    @EventType("calendar.production-due")
    public record ProductionDue(UUID eventId, UUID ownerId, UUID reminderId, String title, Instant productionStartAt, Instant startAt)
            implements DomainEvent {
        @Override public String aggregateType() { return CALENDAR_EVENT; }
        @Override public UUID aggregateId() { return eventId; }
    }
}
