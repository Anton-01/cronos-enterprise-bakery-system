package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.mail.domain.EmailBrand;
import com.ninsky.cronos.mail.domain.EmailSender;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailSettingsChangedEvent;
import com.ninsky.cronos.mail.infrastructure.EmailSettingsCustomRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Brand and sender shared by every email (§4.3): one revision-guarded row per tenant, the {@code app.mail.*} defaults
 * until the first save. A From outside the verified sending domains is refused: an unaligned From silently sends
 * everything to spam.
 */
@Service
public class EmailSettingsService {

    static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final EmailContentProvider provider;
    private final EmailSettingsCustomRepository repository;
    private final EmailAssetService assets;
    private final MailProperties properties;
    private final DomainEventPublisher events;
    private final ApplicationEventPublisher localEvents;
    private final AuditRecorder audit;
    private final Clock clock;

    public EmailSettingsService(EmailContentProvider provider, EmailSettingsCustomRepository repository, EmailAssetService assets,
                                MailProperties properties, DomainEventPublisher events, ApplicationEventPublisher localEvents,
                                AuditRecorder audit, Clock clock) {
        this.provider = provider;
        this.repository = repository;
        this.assets = assets;
        this.properties = properties;
        this.events = events;
        this.localEvents = localEvents;
        this.audit = audit;
        this.clock = clock;
    }

    /** The current settings (cached). */
    public EmailSettings current() {
        return provider.settings(provider.tenant());
    }

    /**
     * Saves brand and sender.
     *
     * @throws ApiException VALIDATION_ERROR / SENDER_DOMAIN_NOT_VERIFIED per field; STALE_REVISION when
     *                      {@code body.revision} is not the stored revision
     */
    @Transactional
    public EmailSettings save(EmailSettings body, Actor actor) {
        validate(body);
        UUID tenant = provider.tenant();
        Optional<EmailSettings> stored = repository.find(tenant);
        Long storedRevision = stored.map(EmailSettings::revision).orElse(null);
        if (!Objects.equals(storedRevision, body.revision())) {
            throw stale(storedRevision);
        }
        EmailSettings normalized = normalize(body);
        EmailSettings previous = stored.orElseGet(provider::defaultSettings);
        Map<String, Object> changes = diff(previous, normalized);
        if (stored.isPresent() && changes.isEmpty()) {
            return stored.get();
        }
        boolean written = stored.isEmpty()
                ? repository.insert(tenant, normalized, actor.id(), clock.instant())
                : repository.update(tenant, storedRevision, normalized, actor.id(), clock.instant());
        if (!written) {
            throw stale(repository.find(tenant).map(EmailSettings::revision).orElse(null));
        }
        long revision = storedRevision == null ? 1 : storedRevision + 1;
        assets.markReferenced(tenant, java.util.stream.Stream.of(normalized.brand().logoUrl(), normalized.brand().headerImageUrl())
                .filter(Objects::nonNull).toList());
        events.publish(new EmailSettingsChangedEvent(tenant, revision, List.copyOf(changes.keySet())));
        audit.record(AuditEvent.of(AuditAction.EMAIL_SETTINGS_UPDATED, AuditTargets.EMAIL_SETTINGS, tenant, "email_settings")
                .params(Map.of("revision", revision))
                .changes(changes)
                .build());
        localEvents.publishEvent(EmailCacheEviction.settings(tenant));
        return normalized.withRevision(revision);
    }

    /**
     * Checks {@code settings} (also used for preview drafts), collecting every problem.
     *
     * @throws ApiException VALIDATION_ERROR / SENDER_DOMAIN_NOT_VERIFIED
     */
    public void validate(EmailSettings settings) {
        Violations violations = new Violations();
        EmailBrand brand = settings.brand();
        EmailSender sender = settings.sender();
        text(violations, "brand.name", brand.name(), 80);
        color(violations, "brand.primaryColor", brand.primaryColor());
        color(violations, "brand.accentColor", brand.accentColor());
        image(violations, "brand.logoUrl", brand.logoUrl());
        image(violations, "brand.headerImageUrl", brand.headerImageUrl());
        email(violations, "brand.supportEmail", brand.supportEmail(), true);
        text(violations, "brand.address", brand.address(), 200);
        https(violations, "brand.preferencesUrl", brand.preferencesUrl(), true);
        text(violations, "sender.fromName", sender.fromName(), 80);
        if (email(violations, "sender.fromAddress", sender.fromAddress(), true)) {
            String domain = sender.fromAddress().strip().substring(sender.fromAddress().strip().lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
            if (!properties.verifiedSendingDomains().contains(domain)) {
                violations.add(ApiErrorCode.SENDER_DOMAIN_NOT_VERIFIED, "sender.fromAddress", "mail.settings.senderDomain", domain);
            }
        }
        email(violations, "sender.replyTo", sender.replyTo(), false);
        violations.throwIfAny();
    }

    /** Changed fields as {@code {from, to}}, keyed by request path ({@code sender.fromAddress}). */
    static Map<String, Object> diff(EmailSettings before, EmailSettings after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        EmailBrand b = before.brand();
        EmailBrand a = after.brand();
        Changes.put(changes, "brand.name", b.name(), a.name());
        Changes.put(changes, "brand.primaryColor", b.primaryColor(), a.primaryColor());
        Changes.put(changes, "brand.accentColor", b.accentColor(), a.accentColor());
        Changes.put(changes, "brand.logoUrl", b.logoUrl(), a.logoUrl());
        Changes.put(changes, "brand.headerImageUrl", b.headerImageUrl(), a.headerImageUrl());
        Changes.put(changes, "brand.supportEmail", b.supportEmail(), a.supportEmail());
        Changes.put(changes, "brand.address", b.address(), a.address());
        Changes.put(changes, "brand.preferencesUrl", b.preferencesUrl(), a.preferencesUrl());
        Changes.put(changes, "sender.fromName", before.sender().fromName(), after.sender().fromName());
        Changes.put(changes, "sender.fromAddress", before.sender().fromAddress(), after.sender().fromAddress());
        Changes.put(changes, "sender.replyTo", before.sender().replyTo(), after.sender().replyTo());
        return changes;
    }

    /** Trimmed values, blank optionals as null, colors lower-cased. */
    static EmailSettings normalize(EmailSettings s) {
        EmailBrand b = s.brand();
        EmailSender sender = s.sender();
        return new EmailSettings(
                new EmailBrand(b.name().strip(), b.primaryColor().toLowerCase(Locale.ROOT), b.accentColor().toLowerCase(Locale.ROOT),
                        blankToNull(b.logoUrl()), blankToNull(b.headerImageUrl()), b.supportEmail().strip(), b.address().strip(),
                        b.preferencesUrl().strip()),
                new EmailSender(sender.fromName().strip(), sender.fromAddress().strip(), blankToNull(sender.replyTo())),
                s.revision());
    }

    private void image(Violations violations, String field, String url) {
        if (!https(violations, field, url, false) || url == null || url.isBlank()) {
            return;
        }
        String host = properties.assets().publicBaseUrl();
        if (properties.assets().enforceHost() && !url.strip().startsWith(host.endsWith("/") ? host : host + "/")) {
            violations.invalid(field, "mail.content.assetHost", host);
        }
    }

    private static void text(Violations violations, String field, String value, int max) {
        if (value == null || value.isBlank()) {
            violations.invalid(field, "mail.content.required");
        } else if (value.strip().length() > max) {
            violations.invalid(field, "mail.content.length", 1, max);
        }
    }

    private static void color(Violations violations, String field, String value) {
        violations.invalidIf(value == null || !COLOR.matcher(value).matches(), field, "mail.settings.color");
    }

    private static boolean https(Violations violations, String field, String value, boolean required) {
        if (value == null || value.isBlank()) {
            violations.invalidIf(required, field, "mail.content.required");
            return !required;
        }
        String trimmed = value.strip();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("https://") || trimmed.length() > 500 || trimmed.contains(" ")) {
            violations.invalid(field, "mail.content.httpsOnly");
            return false;
        }
        return true;
    }

    /** RFC 5322 address (strict parse, single address, ≤ 254 chars); true when valid. */
    private static boolean email(Violations violations, String field, String value, boolean required) {
        if (value == null || value.isBlank()) {
            violations.invalidIf(required, field, "mail.content.required");
            return false;
        }
        String trimmed = value.strip();
        try {
            InternetAddress address = new InternetAddress(trimmed, true);
            address.validate();
            if (trimmed.length() > 254 || !trimmed.equals(address.getAddress()) || trimmed.indexOf('@') < 1) {
                throw new AddressException("not a bare address");
            }
            return true;
        } catch (AddressException e) {
            violations.invalid(field, "mail.settings.email");
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException stale(Long current) {
        return ApiException.of(ApiErrorCode.STALE_REVISION, "revision", "mail.content.staleRevision", current == null ? "—" : current);
    }
}
