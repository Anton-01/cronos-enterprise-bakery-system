package com.ninsky.cronos.calendar.application;

import com.ninsky.cronos.calendar.domain.CalendarEvents;
import com.ninsky.cronos.calendar.domain.ReminderKind;
import com.ninsky.cronos.calendar.domain.ReminderPlan;
import com.ninsky.cronos.calendar.infrastructure.CalendarEventCustomRepository;
import com.ninsky.cronos.calendar.infrastructure.CalendarReminderCustomRepository;
import com.ninsky.cronos.calendar.infrastructure.CalendarReminderCustomRepository.DueReminder;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.PublishOptions;
import com.ninsky.cronos.eventing.domain.TimeOrderedIds;
import com.ninsky.cronos.iam.shared.TenantTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reminders of calendar entries (§5.6): materialised from the entry by an outbox projection after every change, and
 * published once when due by the reminder job.
 */
@Service
public class CalendarReminders {

    /** A reminder found later than this after its entry started (downtime) is dropped instead of sent. */
    static final Duration LATE_TOLERANCE = Duration.ofMinutes(15);

    private final CalendarEventCustomRepository events;
    private final CalendarReminderCustomRepository reminders;
    private final DomainEventPublisher publisher;
    private final Clock clock;

    public CalendarReminders(CalendarEventCustomRepository events, CalendarReminderCustomRepository reminders, DomainEventPublisher publisher,
                             Clock clock) {
        this.events = events;
        this.reminders = reminders;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Recomputes the pending reminders of an entry from its current state (none once deleted, cancelled or delivered). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void rematerialize(UUID eventId) {
        Instant now = TenantTime.now(clock);
        events.find(eventId).ifPresentOrElse(
                row -> reminders.replacePending(eventId,
                        ReminderPlan.plan(row.status(), row.startAt(), row.reminders(), row.productionStartAt(), now),
                        () -> TimeOrderedIds.next(clock)),
                () -> reminders.clearPending(eventId));
    }

    /**
     * Publishes the due reminders ({@code calendar.reminder-due} / {@code calendar.production-due}) and marks them sent.
     * Reminders of entries that are no longer active, or that already started, are only marked.
     *
     * @return how many were published
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int publishDue(int limit) {
        Instant now = TenantTime.now(clock);
        List<DueReminder> due = reminders.claimDue(now, limit);
        int published = 0;
        for (DueReminder reminder : due) {
            if (reminder.deleted() || !reminder.status().active() || !reminder.startAt().isAfter(now.minus(LATE_TOLERANCE))) {
                continue;
            }
            PublishOptions once = PublishOptions.dedupe("calendar-reminder:" + reminder.id());
            if (reminder.kind() == ReminderKind.PRODUCTION_START) {
                publisher.publish(new CalendarEvents.ProductionDue(reminder.eventId(), reminder.ownerId(), reminder.id(), reminder.title(),
                        reminder.productionStartAt(), reminder.startAt()), once);
            } else {
                publisher.publish(new CalendarEvents.CalendarReminderDue(reminder.eventId(), reminder.ownerId(), reminder.id(),
                        reminder.minutesBefore() == null ? 0 : reminder.minutesBefore(), reminder.title(), reminder.startAt(),
                        reminder.allDay()), once);
            }
            published++;
        }
        reminders.markSent(due.stream().map(DueReminder::id).toList(), now);
        return published;
    }
}
