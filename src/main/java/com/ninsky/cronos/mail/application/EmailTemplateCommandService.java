package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.eventing.application.TraceContext;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.mail.api.EmailTemplateContent;
import com.ninsky.cronos.mail.api.EmailTemplateResponse;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailTemplateCustomizedEvent;
import com.ninsky.cronos.mail.domain.EmailTemplateEvents.EmailTemplateResetEvent;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository.OverrideRow;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Edits of one template locale (§4.1, §4.2): save an override with optimistic locking on {@code revision}, or delete it
 * to serve the built-in default again. Each write stores a history row, publishes its outbox event, writes the IAM audit
 * entry (actor, before/after diff) in the same transaction, and evicts the caches after commit.
 */
@Service
public class EmailTemplateCommandService {

    static final String SAVED = "SAVED";
    static final String RESET = "RESET";

    private final EmailContentProvider provider;
    private final EmailOverrideCustomRepository overrides;
    private final ContentValidator validator;
    private final EmailAssetService assets;
    private final EmailTemplateQueryService queries;
    private final DomainEventPublisher events;
    private final ApplicationEventPublisher localEvents;
    private final AuditRecorder audit;
    private final Clock clock;

    public EmailTemplateCommandService(EmailContentProvider provider, EmailOverrideCustomRepository overrides, ContentValidator validator,
                                       EmailAssetService assets, EmailTemplateQueryService queries, DomainEventPublisher events,
                                       ApplicationEventPublisher localEvents, AuditRecorder audit, Clock clock) {
        this.provider = provider;
        this.overrides = overrides;
        this.validator = validator;
        this.assets = assets;
        this.queries = queries;
        this.events = events;
        this.localEvents = localEvents;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Saves the content of {@code (key, locale)}. Content equal to the built-in default deletes the override instead of
     * storing a copy, so {@code customizedLocales} stays honest; content equal to the stored override changes nothing.
     *
     * @throws ApiException EMAIL_TEMPLATE_NOT_FOUND; VALIDATION_ERROR / UNKNOWN_VARIABLE per field; STALE_REVISION when
     *                      {@code body.revision} is not the stored revision (null while an override exists)
     */
    @Transactional
    public EmailTemplateResponse save(String key, EmailLocale locale, EmailTemplateContent body, Actor actor) {
        TemplateDefinition template = provider.requireDefinition(key);
        LocalizedEmail content = body.toContent();
        validator.validate(template, content);
        UUID tenant = provider.tenant();
        Optional<OverrideRow> existing = overrides.find(tenant, key, locale);
        Long stored = existing.map(OverrideRow::revision).orElse(null);
        if (!Objects.equals(stored, body.revision())) {
            throw stale(stored);
        }
        LocalizedEmail defaults = template.content(locale);
        if (content.equals(defaults)) {
            if (existing.isPresent()) {
                overrides.delete(tenant, key, locale, stored)
                        .ifPresentOrElse(gone -> recordReset(template, locale, gone, actor), () -> {
                            throw stale(overrides.find(tenant, key, locale).map(OverrideRow::revision).orElse(null));
                        });
            }
            return queries.template(key);
        }
        LocalizedEmail previous = existing.map(OverrideRow::content).orElse(defaults);
        if (existing.isPresent() && content.equals(previous)) {
            return queries.template(key);
        }
        Instant now = clock.instant();
        long revision;
        if (existing.isEmpty()) {
            if (!overrides.insert(tenant, key, locale, content, actor.id(), now)) {
                throw stale(overrides.find(tenant, key, locale).map(OverrideRow::revision).orElse(null));
            }
            revision = 1;
        } else {
            if (!overrides.update(tenant, key, locale, stored, content, actor.id(), now)) {
                throw stale(overrides.find(tenant, key, locale).map(OverrideRow::revision).orElse(null));
            }
            revision = stored + 1;
        }
        overrides.history(tenant, key, locale, revision, SAVED, content, actor.id(), now, traceId());
        assets.markReferenced(tenant, content.blocks().stream().flatMap(EmailBlock::texts).toList());
        Map<String, Object> changes = diff(previous, content);
        events.publish(new EmailTemplateCustomizedEvent(tenant, key, locale, revision, List.copyOf(changes.keySet())));
        audit.record(AuditEvent.of(AuditAction.EMAIL_TEMPLATE_CUSTOMIZED, AuditTargets.EMAIL_TEMPLATE, key + "/" + locale.tag(), key)
                .params(Map.of("locale", locale.tag(), "revision", revision))
                .changes(changes)
                .build());
        localEvents.publishEvent(EmailCacheEviction.content(tenant, key, locale));
        return queries.template(key);
    }

    /**
     * Deletes the override of {@code (key, locale)}. Idempotent: a missing override answers the template unchanged.
     *
     * @throws ApiException EMAIL_TEMPLATE_NOT_FOUND
     */
    @Transactional
    public EmailTemplateResponse reset(String key, EmailLocale locale, Actor actor) {
        TemplateDefinition template = provider.requireDefinition(key);
        overrides.delete(provider.tenant(), key, locale).ifPresent(row -> recordReset(template, locale, row, actor));
        return queries.template(key);
    }

    private void recordReset(TemplateDefinition template, EmailLocale locale, OverrideRow removed, Actor actor) {
        UUID tenant = provider.tenant();
        overrides.history(tenant, template.key(), locale, removed.revision(), RESET, null, actor.id(), clock.instant(), traceId());
        events.publish(new EmailTemplateResetEvent(tenant, template.key(), locale, removed.revision()));
        audit.record(AuditEvent.of(AuditAction.EMAIL_TEMPLATE_RESET, AuditTargets.EMAIL_TEMPLATE, template.key() + "/" + locale.tag(),
                        template.key())
                .params(Map.of("locale", locale.tag(), "previousRevision", removed.revision()))
                .changes(diff(removed.content(), template.content(locale)))
                .build());
        localEvents.publishEvent(EmailCacheEviction.content(tenant, template.key(), locale));
    }

    /** Changed fields as {@code {from, to}}; blocks are compared as a whole. */
    static Map<String, Object> diff(LocalizedEmail before, LocalizedEmail after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        Changes.put(changes, "subject", before.subject(), after.subject());
        Changes.put(changes, "preheader", before.preheader(), after.preheader());
        Changes.put(changes, "blocks", new ArrayList<>(before.blocks()), new ArrayList<>(after.blocks()));
        return changes;
    }

    private static ApiException stale(Long current) {
        return ApiException.of(ApiErrorCode.STALE_REVISION, "revision", "mail.content.staleRevision", current == null ? "—" : current);
    }

    private static String traceId() {
        return TraceContexts.current().map(TraceContext::traceId).orElse(null);
    }
}
