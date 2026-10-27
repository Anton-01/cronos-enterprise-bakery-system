package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.domain.RetrySchedule;
import com.ninsky.cronos.mail.application.CompiledTemplate;
import com.ninsky.cronos.mail.application.EmailContentProvider;
import com.ninsky.cronos.mail.application.EmailRenderer;
import com.ninsky.cronos.mail.application.MailProperties;
import com.ninsky.cronos.mail.application.MailRequested;
import com.ninsky.cronos.mail.application.RenderedEmail;
import com.ninsky.cronos.mail.domain.ComposedEmail;
import com.ninsky.cronos.mail.domain.EmailComposer;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outbox consumer of {@link MailRequested}: composes the effective template (override ?? default, read through the
 * email caches: no database read once warm), renders it with the tenant's brand and sends it through the
 * {@link MailGateway} as {@code "fromName" <fromAddress>} with the settings' {@code Reply-To}. Every attempt is logged in {@code mail_messages}; a failure is rethrown so the outbox retries on
 * the mail schedule (1 min, 5 min, 30 min, 2 h, 6 h) and the row ends {@code FAILED} after the last one.
 */
@Slf4j
@Component
public class MailDispatcher implements OutboxHandler<MailRequested> {

    private final EmailContentProvider provider;
    private final EmailRenderer renderer;
    private final MailGateway gateway;
    private final MailMessageCustomRepository mailLog;
    private final MailProperties properties;
    private final TransactionTemplate independent;
    private final MeterRegistry meters;
    private final Clock clock;

    public MailDispatcher(EmailContentProvider provider, EmailRenderer renderer, MailGateway gateway, MailMessageCustomRepository mailLog,
                          MailProperties properties, PlatformTransactionManager transactions, MeterRegistry meters, Clock clock) {
        this.provider = provider;
        this.renderer = renderer;
        this.gateway = gateway;
        this.mailLog = mailLog;
        this.properties = properties;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.meters = meters;
        this.clock = clock;
    }

    @Override
    public Class<MailRequested> eventClass() {
        return MailRequested.class;
    }

    @Override
    public void handle(MailRequested event, EventEnvelope envelope) {
        int attempt = envelope.attempts() + 1;
        TemplateDefinition template = provider.definition(event.templateKey())
                .orElseThrow(() -> new IllegalStateException("Unknown email template " + event.templateKey()));
        CompiledTemplate compiled = provider.compiled(provider.tenant(), template, EmailLocale.normalize(event.locale()));
        EmailSettings settings = compiled.settings();
        LocalizedEmail content = event.draft() != null ? event.draft() : compiled.content().content();
        ComposedEmail composed = EmailComposer.compose(template, compiled.content().locale(), content, event.variables(),
                settings.implicitVariables());
        if (event.test()) {
            composed = composed.withSubjectPrefix("[TEST] ");
        }
        RenderedEmail rendered = renderer.render(composed, settings);
        OutgoingMail mail = new OutgoingMail("<" + envelope.id() + "@" + properties.messageIdDomain() + ">",
                settings.sender().fromAddress(), settings.sender().fromName(), settings.sender().replyTo(), event.to(), rendered.subject(),
                rendered.html(), rendered.text(), headers(template, settings, envelope));
        try {
            String providerId = gateway.send(mail);
            mailLog.record(envelope.id(), template.key(), composed.locale(), event.to(), MailMessageCustomRepository.Status.SENT,
                    attempt, providerId, null, clock.instant(), envelope.traceId());
            count(template.key(), "sent");
        } catch (RuntimeException e) {
            boolean last = RetrySchedule.MAIL.exhausted(attempt);
            MailMessageCustomRepository.Status status = last ? MailMessageCustomRepository.Status.FAILED
                    : MailMessageCustomRepository.Status.RETRYING;
            independent.executeWithoutResult(tx -> mailLog.record(envelope.id(), template.key(), event.locale(), event.to(), status,
                    attempt, null, e.getMessage(), clock.instant(), envelope.traceId()));
            count(template.key(), last ? "failed" : "retry");
            if (last) {
                log.error("Giving up on email {} ({}) after {} attempts: {}", envelope.id(), template.key(), attempt,
                        e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public String name() {
        return "MailDispatcher";
    }

    private static Map<String, String> headers(TemplateDefinition template, EmailSettings settings, EventEnvelope envelope) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Cronos-Template", template.key());
        if (envelope.traceId() != null) {
            headers.put("X-Cronos-Trace", envelope.traceId());
        }
        if (!template.transactional()) {
            headers.put("List-Unsubscribe", "<mailto:" + settings.brand().supportEmail() + "?subject=unsubscribe>, <"
                    + settings.brand().preferencesUrl() + ">");
            headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        }
        return headers;
    }

    private void count(String template, String outcome) {
        meters.counter("cronos.mail.sent", "template", template, "outcome", outcome).increment();
    }
}
