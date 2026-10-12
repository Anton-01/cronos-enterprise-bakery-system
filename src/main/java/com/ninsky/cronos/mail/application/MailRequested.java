package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;
import com.ninsky.cronos.eventing.domain.RetrySchedule;
import com.ninsky.cronos.eventing.domain.Sensitive;

import java.util.Map;
import java.util.UUID;

/**
 * An email to render and send (the {@code EmailMessage} outbox payload of §7.4). Recipient and variables are
 * {@link Sensitive}: they may hold a reset link or a temporary password, so they are encrypted at rest and never
 * shown in the event trail. Each email is its own aggregate, so a failing one never delays another.
 *
 * @param recipientUserId the account the email goes to, null for clients without an account
 * @param test            administrator test send: subject prefixed with {@code [TEST]}
 */
@EventType(value = "mail.requested", retry = RetrySchedule.MAIL)
public record MailRequested(UUID messageId, String templateKey, String locale, @Sensitive String to,
                            @Sensitive Map<String, String> variables, UUID recipientUserId, boolean test)
        implements DomainEvent {

    public MailRequested {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    @Override
    public String aggregateType() {
        return "MAIL";
    }

    @Override
    public UUID aggregateId() {
        return messageId;
    }

    @Override
    public String toString() {
        return "MailRequested[messageId=" + messageId + ", templateKey=" + templateKey + "]";
    }
}
