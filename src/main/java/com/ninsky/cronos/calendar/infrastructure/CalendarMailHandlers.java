package com.ninsky.cronos.calendar.infrastructure;

import com.ninsky.cronos.calendar.domain.CalendarEventType;
import com.ninsky.cronos.calendar.domain.CalendarEvents;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.mail.application.MailFormats;
import com.ninsky.cronos.mail.application.MailRequest;
import com.ninsky.cronos.mail.application.Mailer;
import com.ninsky.cronos.mail.domain.MailLocales;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;

/**
 * Emails of the orders calendar, each an independent outbox consumer: the order confirmation to the client
 * ({@code calendar.order-scheduled-client}) and the reminder to the baker ({@code calendar.order-reminder}). Both read
 * the entry when they run, so a deleted or edited entry is never announced with stale data.
 */
@Configuration
public class CalendarMailHandlers {

    private final CalendarEventCustomRepository events;
    private final UserReadCustomRepository users;
    private final UserProfileRepositoryPort profiles;
    private final KitchenMessages messages;
    private final Mailer mailer;
    private final String appUrl;

    public CalendarMailHandlers(CalendarEventCustomRepository events, UserReadCustomRepository users, UserProfileRepositoryPort profiles,
                                KitchenMessages messages, Mailer mailer, @Value("${app.frontend.url}") String appUrl) {
        this.events = events;
        this.users = users;
        this.profiles = profiles;
        this.messages = messages;
        this.mailer = mailer;
        this.appUrl = appUrl;
    }

    /** The client learns when and how the order is delivered (clients have no account: the baker's language). */
    @Bean
    OutboxHandler<CalendarEvents.OrderScheduled> orderScheduledClientMail() {
        return OutboxHandlers.of(CalendarEvents.OrderScheduled.class, "OrderScheduledClientMail", (event, envelope) ->
                events.find(event.eventId())
                        .filter(row -> row.status().active() && row.clientEmail() != null && !row.clientEmail().isBlank())
                        .ifPresent(row -> {
                            String locale = users.find(row.ownerId()).map(UserRow::locale).orElse(MailLocales.SPANISH);
                            mailer.send(MailRequest.of("calendar.order-scheduled-client", locale, row.clientEmail(), Map.of(
                                    "clientName", Objects.requireNonNullElse(row.clientName(), ""),
                                    "bakerName", bakerName(row.ownerId()),
                                    "quoteNumber", Objects.requireNonNullElse(row.quoteNumber(), ""),
                                    "startsAt", MailFormats.dateTime(row.startAt(), row.allDay(), TenantTime.ZONE, locale),
                                    "location", Objects.requireNonNullElse(row.location(), ""),
                                    "kind", messages.get(MailLocales.toLocale(locale), kindKey(row.type())))));
                        }));
    }

    /** The baker's reminder before an order or appointment, with the production sheet link. */
    @Bean
    OutboxHandler<CalendarEvents.CalendarReminderDue> orderReminderMail() {
        return OutboxHandlers.of(CalendarEvents.CalendarReminderDue.class, "CalendarReminderMail", (event, envelope) ->
                events.find(event.eventId()).filter(row -> row.status().active()).ifPresent(row ->
                        users.find(row.ownerId()).ifPresent(baker -> mailer.send(MailRequest.of("calendar.order-reminder", baker.locale(),
                                        baker.email(), Map.of(
                                                "displayName", baker.displayName(),
                                                "title", row.title(),
                                                "startsAt", MailFormats.dateTime(row.startAt(), row.allDay(), TenantTime.ZONE, baker.locale()),
                                                "location", Objects.requireNonNullElse(row.location(), ""),
                                                "clientName", Objects.requireNonNullElse(row.clientName(), ""),
                                                "productionUrl", appUrl + "/cronos/calendario/eventos/" + row.id() + "/produccion"))
                                .forUser(baker.id())))));
    }

    private String bakerName(java.util.UUID ownerId) {
        return profiles.findByUserId(ownerId).map(UserProfile::getBakerCompleteName)
                .or(() -> users.find(ownerId).map(UserRow::displayName))
                .orElse("");
    }

    private static String kindKey(CalendarEventType type) {
        return type == CalendarEventType.ORDER_PICKUP ? "calendar.kind.pickup" : "calendar.kind.delivery";
    }
}
