package com.ninsky.cronos.notification.application;

import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.PublishOptions;
import com.ninsky.cronos.eventing.domain.TimeOrderedIds;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.mail.domain.MailLocales;
import com.ninsky.cronos.notification.domain.NotificationChannel;
import com.ninsky.cronos.notification.domain.NotificationEvents;
import com.ninsky.cronos.notification.domain.NotificationRouter;
import com.ninsky.cronos.notification.domain.NotificationType;
import com.ninsky.cronos.notification.domain.RecipientSettings;
import com.ninsky.cronos.notification.infrastructure.NotificationCustomRepository;
import com.ninsky.cronos.notification.infrastructure.NotificationCustomRepository.NotificationRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a domain fact into notifications (§6.6): for each recipient it routes the type through the recipient's
 * preferences, renders the texts in their language, stores the inbox row (IN_APP) and publishes
 * {@code notification.created} for the live stream and the push consumer. Idempotent per source event, recipient and
 * type, so a redelivered event creates nothing twice. Runs inside the projector's transaction.
 */
@Service
public class Notifier {

    static final int LINK_MAX = 300;

    /** What to notify about. */
    public record Notice(NotificationType type, Map<String, ?> args, String link, String subjectType, UUID subjectId, String actorName) {

        public Notice {
            args = Map.copyOf(args);
            if (link != null && (!link.startsWith("/") || link.startsWith("//") || link.length() > LINK_MAX)) {
                throw new IllegalArgumentException("Notification links are in-app paths: " + link);
            }
        }
    }

    private final NotificationSettingsService settings;
    private final NotificationTexts texts;
    private final NotificationCustomRepository inbox;
    private final UserReadCustomRepository users;
    private final DomainEventPublisher publisher;
    private final Clock clock;

    public Notifier(NotificationSettingsService settings, NotificationTexts texts, NotificationCustomRepository inbox,
                    UserReadCustomRepository users, DomainEventPublisher publisher, Clock clock) {
        this.settings = settings;
        this.texts = texts;
        this.inbox = inbox;
        this.users = users;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Notifies every recipient of {@code notice}, caused by the event in {@code source}. Returns how many were new. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int notify(Collection<UUID> recipients, Notice notice, EventEnvelope source) {
        if (recipients.isEmpty()) {
            return 0;
        }
        Map<UUID, RecipientSettings> routing = settings.recipients(recipients);
        Instant now = clock.instant();
        int created = 0;
        for (UUID recipient : List.copyOf(routing.keySet())) {
            Optional<UserRow> user = users.find(recipient);
            if (user.isEmpty()) {
                continue;
            }
            RecipientSettings recipientSettings = routing.get(recipient);
            EnumSet<NotificationChannel> channels = NotificationRouter.route(recipientSettings, notice.type(), now);
            boolean inApp = channels.contains(NotificationChannel.IN_APP);
            boolean push = channels.contains(NotificationChannel.PUSH);
            if (!inApp && !push) {
                continue;
            }
            NotificationTexts.Rendered rendered = texts.render(notice.type(), notice.args(),
                    MailLocales.toLocale(Optional.ofNullable(user.get().locale()).orElse(MailLocales.SPANISH)), recipientSettings.zone());
            UUID id = TimeOrderedIds.next(clock);
            boolean stored = inApp && inbox.insert(new NotificationRow(id, recipient, notice.type(), notice.type().category(),
                    notice.type().severity(), rendered.title(), rendered.body(), notice.link(), notice.subjectType(), notice.subjectId(),
                    notice.actorName(), now, null), source.id());
            if (inApp && !stored) {
                continue;
            }
            boolean fresh = publisher.publish(new NotificationEvents.NotificationCreated(id, recipient, notice.type(), notice.type().severity(),
                            rendered.title(), rendered.body(), notice.link(), notice.subjectType(), notice.subjectId(), now, stored, push),
                    PublishOptions.dedupe("notification:" + source.id() + ":" + recipient + ":" + notice.type()));
            created += fresh ? 1 : 0;
        }
        return created;
    }
}
