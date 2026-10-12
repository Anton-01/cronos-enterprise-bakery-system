package com.ninsky.cronos.notification.infrastructure;

import com.ninsky.cronos.calendar.domain.CalendarEvents;
import com.ninsky.cronos.calendar.infrastructure.CalendarEventCustomRepository;
import com.ninsky.cronos.calendar.infrastructure.CalendarEventCustomRepository.EventRow;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.iam.access.PermissionHolderCustomRepository;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.identity.domain.RegistrationEvents;
import com.ninsky.cronos.notification.application.Notifier;
import com.ninsky.cronos.notification.application.Notifier.Notice;
import com.ninsky.cronos.notification.domain.NotificationType;
import com.ninsky.cronos.quote.domain.QuoteActorType;
import com.ninsky.cronos.quote.domain.QuoteEvents;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Projects domain events into notifications (§6.1, §6.6): one consumer per source event, each resolving the recipients,
 * the type, the arguments and the in-app link, then handing them to the {@link Notifier}. Only facts the recipient did
 * not cause themselves are notified (a client's answer, a reminder, a new sign-in), plus order changes across devices.
 */
@Configuration
public class NotificationProjector {

    static final String QUOTE_LINK = "/cronos/cotizaciones/detalles/";
    static final String CALENDAR_LINK = "/cronos/calendario?event=";
    static final String REGISTRATIONS_LINK = "/cronos/admin/solicitudes-registro";
    static final String ACCOUNT_LINK = "/cronos/perfil";
    static final String SECURITY_LINK = "/cronos/perfil/seguridad";

    private final Notifier notifier;
    private final CalendarEventCustomRepository calendar;
    private final UserReadCustomRepository users;
    private final PermissionHolderCustomRepository holders;

    public NotificationProjector(Notifier notifier, CalendarEventCustomRepository calendar, UserReadCustomRepository users,
                                 PermissionHolderCustomRepository holders) {
        this.notifier = notifier;
        this.calendar = calendar;
        this.users = users;
        this.holders = holders;
    }

    // ─── quotes ────────────────────────────────────────────────────────────────────────

    @Bean
    OutboxHandler<QuoteEvents.QuoteViewed> notifyQuoteViewed() {
        return OutboxHandlers.of(QuoteEvents.QuoteViewed.class, "NotifyQuoteViewed", (event, envelope) ->
                notifier.notify(List.of(event.ownerId()), new Notice(NotificationType.QUOTE_VIEWED,
                        args("quoteNumber", event.quoteNumber(), "clientName", event.clientName()),
                        QUOTE_LINK + event.quoteId(), "QUOTE", event.quoteId(), event.clientName()), envelope));
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteAccepted> notifyQuoteAccepted() {
        return OutboxHandlers.of(QuoteEvents.QuoteAccepted.class, "NotifyQuoteAccepted", (event, envelope) -> {
            if (event.actor() == QuoteActorType.CLIENT) {
                notifier.notify(List.of(event.ownerId()), new Notice(NotificationType.QUOTE_ACCEPTED,
                        args("quoteNumber", event.quoteNumber(), "clientName", event.actorName(), "total", event.total(),
                                "currency", event.currency()),
                        QUOTE_LINK + event.quoteId() + "?schedule=1", "QUOTE", event.quoteId(), event.actorName()), envelope);
            }
        });
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteRejected> notifyQuoteRejected() {
        return OutboxHandlers.of(QuoteEvents.QuoteRejected.class, "NotifyQuoteRejected", (event, envelope) -> {
            if (event.actor() == QuoteActorType.CLIENT) {
                notifier.notify(List.of(event.ownerId()), new Notice(NotificationType.QUOTE_REJECTED,
                        args("quoteNumber", event.quoteNumber(), "clientName", event.actorName(), "comment", event.comment()),
                        QUOTE_LINK + event.quoteId(), "QUOTE", event.quoteId(), event.actorName()), envelope);
            }
        });
    }

    @Bean
    OutboxHandler<QuoteEvents.QuoteExpiring> notifyQuoteExpiring() {
        return OutboxHandlers.of(QuoteEvents.QuoteExpiring.class, "NotifyQuoteExpiring", (event, envelope) ->
                notifier.notify(List.of(event.ownerId()), new Notice(NotificationType.QUOTE_EXPIRING,
                        args("quoteNumber", event.quoteNumber(), "validUntil", event.validUntil()),
                        QUOTE_LINK + event.quoteId(), "QUOTE", event.quoteId(), null), envelope));
    }

    // ─── orders and calendar ───────────────────────────────────────────────────────────

    @Bean
    OutboxHandler<CalendarEvents.OrderScheduled> notifyOrderScheduled() {
        return OutboxHandlers.of(CalendarEvents.OrderScheduled.class, "NotifyOrderScheduled", (event, envelope) ->
                entry(event.eventId()).ifPresent(row -> calendarNotice(row, NotificationType.ORDER_SCHEDULED, envelope)));
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventRescheduled> notifyOrderRescheduled() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventRescheduled.class, "NotifyOrderRescheduled", (event, envelope) ->
                entry(event.eventId()).filter(row -> row.type().order() && row.status().active())
                        .ifPresent(row -> calendarNotice(row, NotificationType.ORDER_RESCHEDULED, envelope)));
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarEventCancelled> notifyOrderCancelled() {
        return OutboxHandlers.of(CalendarEvents.CalendarEventCancelled.class, "NotifyOrderCancelled", (event, envelope) ->
                entry(event.eventId()).filter(row -> row.type().order())
                        .ifPresent(row -> calendarNotice(row, NotificationType.ORDER_CANCELLED, envelope)));
    }

    @Bean
    OutboxHandler<CalendarEvents.CalendarReminderDue> notifyEventReminder() {
        return OutboxHandlers.of(CalendarEvents.CalendarReminderDue.class, "NotifyEventReminder", (event, envelope) ->
                entry(event.eventId()).filter(row -> row.status().active())
                        .ifPresent(row -> calendarNotice(row, NotificationType.EVENT_REMINDER, envelope)));
    }

    @Bean
    OutboxHandler<CalendarEvents.ProductionDue> notifyProductionDue() {
        return OutboxHandlers.of(CalendarEvents.ProductionDue.class, "NotifyProductionDue", (event, envelope) ->
                entry(event.eventId()).filter(row -> row.status().active())
                        .ifPresent(row -> calendarNotice(row, NotificationType.PRODUCTION_DUE, envelope)));
    }

    // ─── registration and account ──────────────────────────────────────────────────────

    @Bean
    OutboxHandler<RegistrationEvents.RegistrationSubmitted> notifyRegistrationSubmitted() {
        return OutboxHandlers.of(RegistrationEvents.RegistrationSubmitted.class, "NotifyRegistrationSubmitted", (event, envelope) -> {
            String applicant = users.find(event.userId()).map(UserRow::displayName).orElse("");
            List<UUID> reviewers = holders.holders(Permissions.IAM_REGISTRATION_DECIDE).stream()
                    .filter(id -> !id.equals(event.userId())).toList();
            notifier.notify(reviewers, new Notice(NotificationType.REGISTRATION_SUBMITTED,
                    args("reference", event.reference(), "applicant", applicant),
                    REGISTRATIONS_LINK, "REGISTRATION", event.registrationId(), applicant), envelope);
        });
    }

    @Bean
    OutboxHandler<RegistrationEvents.RegistrationApproved> notifyRegistrationApproved() {
        return OutboxHandlers.of(RegistrationEvents.RegistrationApproved.class, "NotifyRegistrationApproved", (event, envelope) ->
                notifier.notify(List.of(event.userId()), new Notice(NotificationType.REGISTRATION_APPROVED, Map.of(), ACCOUNT_LINK,
                        "USER", event.userId(), null), envelope));
    }

    @Bean
    OutboxHandler<IdentityEvents.PasswordChanged> notifyPasswordChanged() {
        return OutboxHandlers.of(IdentityEvents.PasswordChanged.class, "NotifyPasswordChanged", (event, envelope) ->
                notifier.notify(List.of(event.userId()), new Notice(NotificationType.PASSWORD_CHANGED, args("changedAt", event.changedAt()),
                        SECURITY_LINK, "USER", event.userId(), null), envelope));
    }

    @Bean
    OutboxHandler<IdentityEvents.NewDeviceSignedIn> notifyNewSignIn() {
        return OutboxHandlers.of(IdentityEvents.NewDeviceSignedIn.class, "NotifyNewSignIn", (event, envelope) ->
                notifier.notify(List.of(event.userId()), new Notice(NotificationType.NEW_SIGN_IN,
                        args("browser", event.browser(), "os", event.operatingSystem(), "ip", event.ipAddress(), "at", event.signedInAt()),
                        SECURITY_LINK, "USER", event.userId(), null), envelope));
    }

    @Bean
    OutboxHandler<IdentityEvents.AccountLocked> notifyAccountLocked() {
        return OutboxHandlers.of(IdentityEvents.AccountLocked.class, "NotifyAccountLocked", (event, envelope) ->
                notifier.notify(List.of(event.userId()), new Notice(NotificationType.ACCOUNT_LOCKED,
                        args("lockedUntil", event.lockedUntil(), "attempts", event.failedAttempts()),
                        SECURITY_LINK, "USER", event.userId(), null), envelope));
    }

    // ─── helpers ───────────────────────────────────────────────────────────────────────

    private Optional<EventRow> entry(UUID eventId) {
        return calendar.find(eventId);
    }

    private void calendarNotice(EventRow row, NotificationType type, EventEnvelope envelope) {
        notifier.notify(List.of(row.ownerId()), new Notice(type,
                args("title", row.title(), "startsAt", row.startAt(), "clientName", row.clientName(), "location", row.location()),
                CALENDAR_LINK + row.id(), "CALENDAR_EVENT", row.id(), null), envelope);
    }

    /** Arguments by name; null values render empty. */
    static Map<String, Object> args(Object... pairs) {
        java.util.LinkedHashMap<String, Object> args = new java.util.LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            args.put(Objects.toString(pairs[index]), pairs[index + 1] == null ? "" : pairs[index + 1]);
        }
        return args;
    }
}
