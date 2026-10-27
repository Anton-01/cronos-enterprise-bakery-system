package com.ninsky.cronos.notification.infrastructure;

import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.iam.access.PermissionHolderCustomRepository;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.mail.application.EmailSettingsService;
import com.ninsky.cronos.mail.domain.EmailSender;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailSettingsChangedEvent;
import com.ninsky.cronos.notification.application.Notifier;
import com.ninsky.cronos.notification.application.Notifier.Notice;
import com.ninsky.cronos.notification.domain.NotificationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code SecurityNotifier} of email-template-customization §6: when the From address or the Reply-To of every email
 * changes (a phishing-relevant change), the other administrators who can edit the email settings are told in-app.
 * Idempotent through the outbox ({@code processed_events}) and the notifier's per-event dedupe.
 */
@Configuration
public class EmailSecurityNotifier {

    static final String SETTINGS_LINK = "/cronos/admin/plantillas-correo?tab=brand";

    private final Notifier notifier;
    private final PermissionHolderCustomRepository holders;
    private final UserReadCustomRepository users;
    private final EmailSettingsService settings;

    public EmailSecurityNotifier(Notifier notifier, PermissionHolderCustomRepository holders, UserReadCustomRepository users,
                                 EmailSettingsService settings) {
        this.notifier = notifier;
        this.holders = holders;
        this.users = users;
        this.settings = settings;
    }

    @Bean
    OutboxHandler<EmailSettingsChangedEvent> notifyEmailSenderChanged() {
        return OutboxHandlers.of(EmailSettingsChangedEvent.class, "SecurityNotifier.emailSender", (event, envelope) -> {
            if (!event.senderIdentityChanged()) {
                return;
            }
            UUID actor = envelope.actorId();
            List<UUID> recipients = holders.holders(Permissions.NOTIFICATION_EMAIL_TEMPLATE_MANAGE).stream()
                    .filter(id -> !id.equals(actor)).toList();
            String actorName = actor == null ? "" : users.find(actor).map(UserRow::displayName).orElse("");
            EmailSender sender = settings.current().sender();
            notifier.notify(recipients, new Notice(NotificationType.EMAIL_SENDER_CHANGED,
                    Map.of("actor", actorName, "fromAddress", sender.fromAddress(), "replyTo", sender.replyTo() == null ? "—" : sender.replyTo()),
                    SETTINGS_LINK, "EMAIL_SETTINGS", event.tenantId(), actorName), envelope);
        });
    }
}
