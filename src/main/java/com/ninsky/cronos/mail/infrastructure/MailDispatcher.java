package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.domain.RetrySchedule;
import com.ninsky.cronos.mail.application.EmailRenderer;
import com.ninsky.cronos.mail.application.MailProperties;
import com.ninsky.cronos.mail.application.MailRequested;
import com.ninsky.cronos.mail.application.RenderedEmail;
import com.ninsky.cronos.mail.domain.ComposedEmail;
import com.ninsky.cronos.mail.domain.EmailComposer;
import com.ninsky.cronos.mail.domain.EmailTemplate;
import com.ninsky.cronos.mail.domain.EmailTemplateCatalog;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outbox consumer of {@link MailRequested}: composes the catalog template, renders it and sends it through the
 * {@link MailGateway}. Every attempt is logged in {@code mail_messages}; a failure is rethrown so the outbox retries on
 * the mail schedule (1 min, 5 min, 30 min, 2 h, 6 h) and the row ends {@code FAILED} after the last one.
 */
@Slf4j
@Component
public class MailDispatcher implements OutboxHandler<MailRequested> {

    static final String SETTINGS_PATH = "/cronos/cuenta/notificaciones";

    private final EmailTemplateCatalog catalog;
    private final EmailRenderer renderer;
    private final MailGateway gateway;
    private final MailMessageCustomRepository mailLog;
    private final MailProperties properties;
    private final TransactionTemplate independent;
    private final MeterRegistry meters;
    private final Clock clock;
    private final String appUrl;

    public MailDispatcher(EmailTemplateCatalog catalog, EmailRenderer renderer, MailGateway gateway, MailMessageCustomRepository mailLog,
                          MailProperties properties, PlatformTransactionManager transactions, MeterRegistry meters, Clock clock,
                          @Value("${app.frontend.url}") String appUrl) {
        this.catalog = catalog;
        this.renderer = renderer;
        this.gateway = gateway;
        this.mailLog = mailLog;
        this.properties = properties;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.meters = meters;
        this.clock = clock;
        this.appUrl = appUrl;
    }

    @Override
    public Class<MailRequested> eventClass() {
        return MailRequested.class;
    }

    @Override
    public void handle(MailRequested event, EventEnvelope envelope) {
        int attempt = envelope.attempts() + 1;
        EmailTemplate template = catalog.find(event.templateKey())
                .orElseThrow(() -> new IllegalStateException("Unknown email template " + event.templateKey()));
        ComposedEmail composed = EmailComposer.compose(template, event.locale(), event.variables(), implicitVariables());
        if (event.test()) {
            composed = composed.withSubjectPrefix("[TEST] ");
        }
        RenderedEmail rendered = renderer.render(composed);
        OutgoingMail mail = new OutgoingMail("<" + envelope.id() + "@" + properties.messageIdDomain() + ">",
                properties.senderAddress(), properties.senderName(), event.to(), rendered.subject(), rendered.html(),
                rendered.text(), headers(template, envelope));
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

    private Map<String, String> implicitVariables() {
        return Map.of("brandName", properties.brandName(), "supportEmail", properties.supportEmail());
    }

    private Map<String, String> headers(EmailTemplate template, EventEnvelope envelope) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Cronos-Template", template.key());
        if (envelope.traceId() != null) {
            headers.put("X-Cronos-Trace", envelope.traceId());
        }
        if (!template.transactional()) {
            String settings = UriComponentsBuilder.fromUriString(appUrl).path(SETTINGS_PATH).build().toUriString();
            headers.put("List-Unsubscribe", "<mailto:" + properties.supportEmail() + "?subject=unsubscribe>, <" + settings + ">");
            headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        }
        return headers;
    }

    private void count(String template, String outcome) {
        meters.counter("cronos.mail.sent", "template", template, "outcome", outcome).increment();
    }
}
