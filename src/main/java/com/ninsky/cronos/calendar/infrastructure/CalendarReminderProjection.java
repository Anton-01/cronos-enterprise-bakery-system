package com.ninsky.cronos.calendar.infrastructure;

import com.ninsky.cronos.calendar.application.CalendarReminders;
import com.ninsky.cronos.calendar.domain.CalendarEvents;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps {@code calendar_reminders} in step with the entry: every change re-plans its pending reminders from the
 * current row, so redeliveries and out-of-date payloads are harmless (events of one entry are dispatched in order).
 */
@Configuration
public class CalendarReminderProjection {

    private final CalendarReminders reminders;

    public CalendarReminderProjection(CalendarReminders reminders) {
        this.reminders = reminders;
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventCreated> remindersOnCreated() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventCreated.class, "CalendarRemindersOnCreated",
                (event, envelope) -> reminders.rematerialize(event.eventId()));
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventUpdated> remindersOnUpdated() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventUpdated.class, "CalendarRemindersOnUpdated",
                (event, envelope) -> reminders.rematerialize(event.eventId()));
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventRescheduled> remindersOnRescheduled() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventRescheduled.class, "CalendarRemindersOnRescheduled",
                (event, envelope) -> reminders.rematerialize(event.eventId()));
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventStatusChanged> remindersOnStatusChanged() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventStatusChanged.class, "CalendarRemindersOnStatusChanged",
                (event, envelope) -> reminders.rematerialize(event.eventId()));
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventDeleted> remindersOnDeleted() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventDeleted.class, "CalendarRemindersOnDeleted",
                (event, envelope) -> reminders.rematerialize(event.eventId()));
    }
}
