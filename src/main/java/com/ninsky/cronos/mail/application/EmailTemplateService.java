package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.KeyedRateLimiter;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.mail.api.EmailPreviewRequest;
import com.ninsky.cronos.mail.api.EmailTestSendRequest;
import com.ninsky.cronos.mail.api.EmailTestSendResult;
import com.ninsky.cronos.mail.domain.EmailComposer;
import com.ninsky.cronos.mail.domain.EmailTemplate;
import com.ninsky.cronos.mail.domain.EmailTemplateCatalog;
import com.ninsky.cronos.mail.domain.MailLocales;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Administrator screens of the email catalog (§7.3): browse, server-side preview and test sends. */
@Service
public class EmailTemplateService {

    private final EmailTemplateCatalog catalog;
    private final EmailRenderer renderer;
    private final MailProperties properties;
    private final Mailer mailer;
    private final UserRepositoryPort users;
    private final KeyedRateLimiter testSends;

    public EmailTemplateService(EmailTemplateCatalog catalog, EmailRenderer renderer, MailProperties properties, Mailer mailer,
                                UserRepositoryPort users) {
        this.catalog = catalog;
        this.renderer = renderer;
        this.properties = properties;
        this.mailer = mailer;
        this.users = users;
        this.testSends = new KeyedRateLimiter(Math.max(1, properties.testSendsPerHour()), Duration.ofHours(1));
    }

    /** The catalog exactly as shipped. */
    public EmailTemplateCatalog catalog() {
        return catalog;
    }

    /**
     * Renders {@code key} with the given variables (samples for the rest).
     *
     * @throws ApiException RESOURCE_NOT_FOUND for an unknown key
     */
    public RenderedEmail preview(String key, EmailPreviewRequest request) {
        EmailTemplate template = template(key);
        return renderer.render(EmailComposer.compose(template, request.locale(), variables(template, request.variables()),
                Map.of("brandName", properties.brandName(), "supportEmail", properties.supportEmail())));
    }

    /**
     * Queues a test send with the subject prefixed {@code [TEST]}.
     *
     * @throws ApiException RESOURCE_NOT_FOUND for an unknown key; VALIDATION_ERROR on {@code to} for an address without
     *                      an account; RATE_LIMITED after {@code app.mail.test-sends-per-hour}
     */
    @Transactional
    public EmailTestSendResult testSend(String key, EmailTestSendRequest request, Actor actor) {
        EmailTemplate template = template(key);
        testSends.acquire(actor.id());
        UUID recipient;
        String to;
        if (request.to() == null || request.to().isBlank()) {
            User self = users.findById(actor.id()).orElseThrow(() -> ApiException.notFound("mail.test.callerUnknown"));
            to = self.getEmail();
            recipient = self.getId();
        } else {
            User other = users.findByEmail(request.to().trim())
                    .orElseThrow(() -> ApiException.invalid("to", "mail.test.recipientOutsideTenant"));
            to = request.to().trim();
            recipient = other.getId();
        }
        UUID messageId = mailer.sendTest(new MailRequest(template.key(), MailLocales.normalize(request.locale()), to,
                variables(template, request.variables()), recipient, null)).orElseThrow();
        return new EmailTestSendResult(messageId);
    }

    private EmailTemplate template(String key) {
        return catalog.find(key).orElseThrow(() -> ApiException.notFound("mail.template.notFound", key));
    }

    private static Map<String, String> variables(EmailTemplate template, Map<String, String> provided) {
        Map<String, String> values = new HashMap<>(template.samples());
        provided.forEach((name, value) -> {
            if (template.variableNames().contains(name) && value != null) {
                values.put(name, value);
            }
        });
        return values;
    }
}
