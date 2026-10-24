package com.ninsky.cronos.mail.domain;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Domain events of email template customization (transactional outbox). The publisher stamps {@code eventId},
 * {@code occurredAt}, the actor, {@code correlationId}, {@code causationId} and {@code traceId}; payloads carry ids and
 * the changed field names only, never the content.
 */
public final class EmailTemplateEvents {

    private static final String TEMPLATE = "EMAIL_TEMPLATE";
    private static final String SETTINGS = "EMAIL_SETTINGS";
    private static final String ASSET = "EMAIL_ASSET";

    private EmailTemplateEvents() {
    }

    /** Stable aggregate id of one {@code (tenant, key, locale)}: events of one template locale are dispatched in order. */
    public static UUID templateAggregate(UUID tenantId, String templateKey, EmailLocale locale) {
        return UUID.nameUUIDFromBytes((tenantId + "|" + templateKey + "|" + locale.tag()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Content of one locale was saved.
     *
     * @param changedFields {@code subject}, {@code preheader}, {@code blocks} (compared with the previous effective content)
     */
    @EventType("mail.template-customized")
    public record EmailTemplateCustomizedEvent(UUID tenantId, String templateKey, EmailLocale locale, long revision,
                                               List<String> changedFields) implements DomainEvent {
        public EmailTemplateCustomizedEvent {
            changedFields = List.copyOf(changedFields);
        }

        @Override public String aggregateType() { return TEMPLATE; }
        @Override public UUID aggregateId() { return templateAggregate(tenantId, templateKey, locale); }
    }

    /** An override was deleted: the locale serves the built-in default again. */
    @EventType("mail.template-reset")
    public record EmailTemplateResetEvent(UUID tenantId, String templateKey, EmailLocale locale, long previousRevision)
            implements DomainEvent {
        @Override public String aggregateType() { return TEMPLATE; }
        @Override public UUID aggregateId() { return templateAggregate(tenantId, templateKey, locale); }
    }

    /**
     * Brand or sender saved.
     *
     * @param changedFields request paths such as {@code sender.fromAddress}, {@code brand.logoUrl}
     */
    @EventType("mail.settings-changed")
    public record EmailSettingsChangedEvent(UUID tenantId, long revision, List<String> changedFields) implements DomainEvent {
        public EmailSettingsChangedEvent {
            changedFields = List.copyOf(changedFields);
        }

        /** True when the From address or the Reply-To changed (phishing-relevant). */
        public boolean senderIdentityChanged() {
            return changedFields.contains("sender.fromAddress") || changedFields.contains("sender.replyTo");
        }

        @Override public String aggregateType() { return SETTINGS; }
        @Override public UUID aggregateId() { return tenantId; }
    }

    /** A new image was stored (not published for a deduplicated re-upload). */
    @EventType("mail.asset-uploaded")
    public record EmailAssetUploadedEvent(UUID tenantId, UUID assetId, String sha256, String contentType, int sizeBytes)
            implements DomainEvent {
        @Override public String aggregateType() { return ASSET; }
        @Override public UUID aggregateId() { return assetId; }
    }
}
