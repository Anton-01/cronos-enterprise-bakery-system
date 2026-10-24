package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.mail.application.EmailRenderer;
import com.ninsky.cronos.mail.application.RenderedEmail;
import com.ninsky.cronos.mail.domain.ComposedEmail;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailSettings;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

/**
 * Renders catalog emails with {@code templates/emails/catalog/message.html}: one table-based, 600 px, inline-styled
 * fragment per block type (the visual spec is the UI preview, {@code email-renderer.ts}). Brand colors, logo, header
 * banner and footer come from the email settings. Every value goes through {@code th:text} / attribute escaping, and
 * colors are validated hex values, so content can never inject markup.
 */
@Component
public class ThymeleafEmailRenderer implements EmailRenderer {

    static final String TEMPLATE = "emails/catalog/message";
    private static final String CALLOUT_BASE = "margin:0 0 20px;padding:14px 18px;font-size:14px;line-height:1.6;color:#334155;border-radius:6px;";
    static final Map<String, String> CALLOUT_STYLES = Map.of(
            "info", CALLOUT_BASE + "border-left:4px solid #3b82f6;background-color:#eff6ff;",
            "success", CALLOUT_BASE + "border-left:4px solid #16a34a;background-color:#f0fdf4;",
            "warning", CALLOUT_BASE + "border-left:4px solid #d97706;background-color:#fffbeb;",
            "danger", CALLOUT_BASE + "border-left:4px solid #dc2626;background-color:#fef2f2;");
    /** Footer labels by locale. */
    static final Map<EmailLocale, String> PREFERENCES_LABEL = Map.of(EmailLocale.ES_MX, "Preferencias de correo", EmailLocale.EN,
            "Email preferences");

    private final SpringTemplateEngine engine;

    public ThymeleafEmailRenderer(SpringTemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public RenderedEmail render(ComposedEmail email, EmailSettings settings) {
        EmailLocale locale = EmailLocale.normalize(email.locale());
        Context context = new Context(locale.locale());
        context.setVariable("email", email);
        context.setVariable("lang", locale == EmailLocale.EN ? "en" : "es");
        context.setVariable("brand", settings.brand());
        context.setVariable("calloutStyles", CALLOUT_STYLES);
        context.setVariable("preferencesLabel", PREFERENCES_LABEL.get(locale));
        return new RenderedEmail(email.subject(), email.preheader(), engine.process(TEMPLATE, context), email.text());
    }
}
