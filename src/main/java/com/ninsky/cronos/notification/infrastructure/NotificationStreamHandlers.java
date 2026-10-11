package com.ninsky.cronos.notification.infrastructure;

import com.ninsky.cronos.calendar.domain.CalendarEvents;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.iam.access.PermissionHolderCustomRepository;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.identity.domain.RegistrationEvents;
import com.ninsky.cronos.notification.api.AppNotification;
import com.ninsky.cronos.notification.application.NotificationInboxService;
import com.ninsky.cronos.notification.application.PushService;
import com.ninsky.cronos.notification.domain.NotificationEvents;
import com.ninsky.cronos.notification.infrastructure.stream.StreamBroadcaster;
import com.ninsky.cronos.notification.infrastructure.stream.StreamFrame;
import com.ninsky.cronos.quote.domain.QuoteEvents;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Consumers that reach the user's devices (§6.3, §6.4): the live {@code notification}, {@code read-state} and
 * {@code domain} frames, the Web Push delivery, and the end of the streams of a revoked or locked session. Frames are
 * best effort (the UI refetches on reconnect); pushes retry on the push schedule.
 */
@Configuration
public class NotificationStreamHandlers {

    private final StreamBroadcaster streams;
    private final NotificationInboxService inbox;
    private final PushService push;
    private final PermissionHolderCustomRepository holders;

    public NotificationStreamHandlers(StreamBroadcaster streams, NotificationInboxService inbox, PushService push,
                                      PermissionHolderCustomRepository holders) {
        this.streams = streams;
        this.inbox = inbox;
        this.push = push;
        this.holders = holders;
    }

    // ─── notifications ─────────────────────────────────────────────────────────────────

    @Bean
    OutboxHandler<NotificationEvents.NotificationCreated> streamNotification() {
        return OutboxHandlers.of(NotificationEvents.NotificationCreated.class, "StreamNotification", (event, envelope) -> {
            if (!event.stored()) {
                return;
            }
            AppNotification notification = new AppNotification(event.notificationId(), event.type(), event.type().category(), event.severity(),
                    event.title(), event.body(), event.link(), event.subjectType(), event.subjectId(), null, event.createdAt(), null);
            streams.publish(StreamFrame.send(event.userId(), "notification", event.notificationId().toString(),
                    inbox.notificationFrame(notification, inbox.unread(event.userId()).total())));
        });
    }

    @Bean
    OutboxHandler<NotificationEvents.NotificationCreated> pushNotification() {
        return OutboxHandlers.of(NotificationEvents.NotificationCreated.class, "PushNotification", push::deliver);
    }

    @Bean
    OutboxHandler<NotificationEvents.NotificationsRead> streamReadState() {
        return OutboxHandlers.of(NotificationEvents.NotificationsRead.class, "StreamReadState", (event, envelope) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", event.all() ? "ALL" : event.ids());
            body.put("unread", event.unread());
            streams.publish(StreamFrame.send(event.userId(), "read-state", null, inbox.write(body)));
        });
    }

    // ─── sessions ──────────────────────────────────────────────────────────────────────

    @Bean
    OutboxHandler<IdentityEvents.SessionRevoked> closeStreamsOnRevoke() {
        return OutboxHandlers.of(IdentityEvents.SessionRevoked.class, "CloseStreamsOnRevoke",
                (event, envelope) -> streams.close(event.userId(), event.sessionId()));
    }

    @Bean
    OutboxHandler<IdentityEvents.SessionLocked> closeStreamsOnLock() {
        return OutboxHandlers.of(IdentityEvents.SessionLocked.class, "CloseStreamsOnLock",
                (event, envelope) -> streams.close(event.userId(), event.sessionId()));
    }

    // ─── domain hints: open screens refetch ────────────────────────────────────────────

    @Bean OutboxHandler<QuoteEvents.QuoteCreated> hintQuoteCreated() { return hint(QuoteEvents.QuoteCreated.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteUpdated> hintQuoteUpdated() { return hint(QuoteEvents.QuoteUpdated.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteSent> hintQuoteSent() { return hint(QuoteEvents.QuoteSent.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteViewed> hintQuoteViewed() { return hint(QuoteEvents.QuoteViewed.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteAccepted> hintQuoteAccepted() { return hint(QuoteEvents.QuoteAccepted.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteRejected> hintQuoteRejected() { return hint(QuoteEvents.QuoteRejected.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteExpired> hintQuoteExpired() { return hint(QuoteEvents.QuoteExpired.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteRevoked> hintQuoteRevoked() { return hint(QuoteEvents.QuoteRevoked.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteDuplicated> hintQuoteDuplicated() { return hint(QuoteEvents.QuoteDuplicated.class, e -> List.of(e.ownerId())); }
    @Bean OutboxHandler<QuoteEvents.QuoteDeleted> hintQuoteDeleted() { return hint(QuoteEvents.QuoteDeleted.class, e -> List.of(e.ownerId())); }

    @Bean OutboxHandler<CalendarEvents.CalendarEventCreated> hintCalendarCreated() {
        return hint(CalendarEvents.CalendarEventCreated.class, e -> List.of(e.ownerId()));
    }
    @Bean OutboxHandler<CalendarEvents.CalendarEventUpdated> hintCalendarUpdated() {
        return hint(CalendarEvents.CalendarEventUpdated.class, e -> List.of(e.ownerId()));
    }
    @Bean OutboxHandler<CalendarEvents.CalendarEventRescheduled> hintCalendarRescheduled() {
        return hint(CalendarEvents.CalendarEventRescheduled.class, e -> List.of(e.ownerId()));
    }
    @Bean OutboxHandler<CalendarEvents.CalendarEventStatusChanged> hintCalendarStatusChanged() {
        return hint(CalendarEvents.CalendarEventStatusChanged.class, e -> List.of(e.ownerId()));
    }
    @Bean OutboxHandler<CalendarEvents.CalendarEventDeleted> hintCalendarDeleted() {
        return hint(CalendarEvents.CalendarEventDeleted.class, e -> List.of(e.ownerId()));
    }

    @Bean OutboxHandler<RegistrationEvents.RegistrationSubmitted> hintRegistrationSubmitted() {
        return hint(RegistrationEvents.RegistrationSubmitted.class, e -> reviewers());
    }
    @Bean OutboxHandler<RegistrationEvents.RegistrationApproved> hintRegistrationApproved() {
        return hint(RegistrationEvents.RegistrationApproved.class, e -> reviewers());
    }
    @Bean OutboxHandler<RegistrationEvents.RegistrationRejected> hintRegistrationRejected() {
        return hint(RegistrationEvents.RegistrationRejected.class, e -> reviewers());
    }
    @Bean OutboxHandler<RegistrationEvents.RegistrationExpired> hintRegistrationExpired() {
        return hint(RegistrationEvents.RegistrationExpired.class, e -> reviewers());
    }

    private List<UUID> reviewers() {
        return holders.holders(Permissions.IAM_REGISTRATION_READ);
    }

    /** A {@code domain} frame (ids only) for every recipient of {@code eventClass}. */
    private <E extends DomainEvent> OutboxHandler<E> hint(Class<E> eventClass, Function<E, Collection<UUID>> recipients) {
        return OutboxHandlers.of(eventClass, "Hint" + eventClass.getSimpleName(), (event, envelope) -> {
            String body = inbox.write(hintBody(envelope));
            recipients.apply(event).stream().distinct().forEach(user -> streams.publish(StreamFrame.send(user, "domain", null, body)));
        });
    }

    private static Map<String, Object> hintBody(EventEnvelope envelope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", envelope.type());
        body.put("aggregateType", envelope.aggregateType());
        body.put("aggregateId", envelope.aggregateId());
        body.put("occurredAt", envelope.occurredAt());
        return body;
    }
}
