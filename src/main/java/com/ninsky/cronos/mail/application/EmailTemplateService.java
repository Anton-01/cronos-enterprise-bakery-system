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
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side preview and test sends (§7.3, §4.4). Either renders the effective content, or an unsaved draft after the
 * same validation as a save (a test must not bypass the {@code UNKNOWN_VARIABLE} / URL rules). Nothing is persisted.
 */
@Service
public class EmailTemplateService {

    private final EmailContentProvider provider;
    private final ContentValidator validator;
    private final EmailSettingsService settings;
    private final EmailRenderer renderer;
    private final Mailer mailer;
    private final UserRepositoryPort users;
    private final KeyedRateLimiter testSends;

    public EmailTemplateService(EmailContentProvider provider, ContentValidator validator, EmailSettingsService settings,
                                EmailRenderer renderer, MailProperties properties, Mailer mailer, UserRepositoryPort users) {
        this.provider = provider;
        this.validator = validator;
        this.settings = settings;
        this.renderer = renderer;
        this.mailer = mailer;
        this.users = users;
        this.testSends = new KeyedRateLimiter(Math.max(1, properties.testSendsPerHour()), Duration.ofHours(1));
    }

    /**
     * Renders {@code key} with the given variables (samples for the rest), using the draft content and settings when sent.
     *
     * @throws ApiException EMAIL_TEMPLATE_NOT_FOUND; VALIDATION_ERROR / UNKNOWN_VARIABLE for an invalid draft
     */
    public RenderedEmail preview(String key, EmailPreviewRequest request) {
        TemplateDefinition template = provider.requireDefinition(key);
        EmailLocale locale = EmailLocale.normalize(request.locale());
        UUID tenant = provider.tenant();
        LocalizedEmail content = request.content() == null ? provider.effective(tenant, template, locale).content() : draft(template, request.content().toContent());
        EmailSettings brand = provider.settings(tenant);
        if (request.settings() != null) {
            settings.validate(request.settings());
            brand = request.settings();
        }
        return renderer.render(EmailComposer.compose(template, locale, content, variables(template, request.variables()),
                brand.implicitVariables()), brand);
    }

    /**
     * Queues a test send with the subject prefixed {@code [TEST]}.
     *
     * @throws ApiException EMAIL_TEMPLATE_NOT_FOUND; VALIDATION_ERROR on {@code to} for an address without an account, or
     *                      per field of an invalid draft; RATE_LIMITED after {@code app.mail.test-sends-per-hour}
     */
    @Transactional
    public EmailTestSendResult testSend(String key, EmailTestSendRequest request, Actor actor) {
        TemplateDefinition template = provider.requireDefinition(key);
        LocalizedEmail draft = request.content() == null ? null : draft(template, request.content().toContent());
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
        UUID messageId = mailer.sendTest(new MailRequest(template.key(), EmailLocale.normalize(request.locale()).tag(), to,
                variables(template, request.variables()), recipient, null), draft).orElseThrow();
        return new EmailTestSendResult(messageId);
    }

    private LocalizedEmail draft(TemplateDefinition template, LocalizedEmail content) {
        validator.validate(template, content);
        return content;
    }

    private static Map<String, String> variables(TemplateDefinition template, Map<String, String> provided) {
        Map<String, String> values = new HashMap<>(template.samples());
        provided.forEach((name, value) -> {
            if (template.variableNames().contains(name) && value != null) {
                values.put(name, value);
            }
        });
        return values;
    }
}
