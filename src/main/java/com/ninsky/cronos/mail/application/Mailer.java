package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.PublishOptions;
import com.ninsky.cronos.eventing.domain.TimeOrderedIds;
import com.ninsky.cronos.mail.domain.EmailTemplate;
import com.ninsky.cronos.mail.domain.EmailTemplateCatalog;
import com.ninsky.cronos.mail.domain.MailLocales;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only way to send email: validates the request against the catalog and enqueues a {@link MailRequested} in the
 * caller's transaction. Rendering and SMTP happen later in the outbox dispatcher, never inside a request.
 */
@Service
public class Mailer {

    private final EmailTemplateCatalog catalog;
    private final DomainEventPublisher events;
    private final ObjectProvider<MailPreferences> preferences;
    private final Clock clock;

    public Mailer(EmailTemplateCatalog catalog, DomainEventPublisher events, ObjectProvider<MailPreferences> preferences, Clock clock) {
        this.catalog = catalog;
        this.events = events;
        this.preferences = preferences;
        this.clock = clock;
    }

    /**
     * Enqueues {@code request}.
     *
     * @return the message id (also the {@code Message-ID} local part), empty when the dedupe key coalesced it or the
     *         recipient turned off the email of a non-transactional template
     * @throws IllegalArgumentException for an unknown template, a blank recipient or a missing declared variable
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> send(MailRequest request) {
        return enqueue(request, false);
    }

    /** {@link #send} for administrator test sends (subject prefixed with {@code [TEST]}). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> sendTest(MailRequest request) {
        return enqueue(request, true);
    }

    private Optional<UUID> enqueue(MailRequest request, boolean test) {
        EmailTemplate template = catalog.find(request.templateKey())
                .orElseThrow(() -> new IllegalArgumentException("Unknown email template " + request.templateKey()));
        if (request.to() == null || request.to().isBlank()) {
            throw new IllegalArgumentException("Email " + request.templateKey() + " has no recipient");
        }
        Set<String> missing = template.variableNames().stream()
                .filter(name -> !request.variables().containsKey(name))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Email " + request.templateKey() + " is missing variables " + missing);
        }
        if (!test && !template.transactional() && request.recipientUserId() != null
                && !preferences.getIfAvailable(() -> MailPreferences.ALLOW_ALL).allows(request.recipientUserId(), template.key())) {
            return Optional.empty();
        }
        UUID messageId = TimeOrderedIds.next(clock);
        MailRequested event = new MailRequested(messageId, template.key(), MailLocales.normalize(request.locale()),
                request.to().trim(), request.variables(), request.recipientUserId(), test);
        boolean published = events.publish(event, request.dedupeKey() == null ? PublishOptions.NONE : PublishOptions.dedupe(request.dedupeKey()));
        return published ? Optional.of(messageId) : Optional.empty();
    }
}
