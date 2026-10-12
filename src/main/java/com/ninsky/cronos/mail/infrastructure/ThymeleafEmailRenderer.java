package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.mail.application.EmailRenderer;
import com.ninsky.cronos.mail.application.MailProperties;
import com.ninsky.cronos.mail.application.RenderedEmail;
import com.ninsky.cronos.mail.domain.ComposedEmail;
import com.ninsky.cronos.mail.domain.MailLocales;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

/**
 * Renders catalog emails with {@code templates/emails/catalog/message.html}: one table-based, 600 px, inline-styled
 * fragment per block type (the visual spec is the UI preview). Every value goes through {@code th:text}, so it is
 * escaped.
 */
@Component
public class ThymeleafEmailRenderer implements EmailRenderer {

    static final String TEMPLATE = "emails/catalog/message";
    private static final String CALLOUT_BASE = "margin:0 0 20px;padding:14px 18px;font-size:14px;line-height:1.6;color:#5C4452;";
    static final Map<String, String> CALLOUT_STYLES = Map.of(
            "info", CALLOUT_BASE + "border-left:4px solid #C7B8DC;background-color:#EAE3F3;",
            "success", CALLOUT_BASE + "border-left:4px solid #4C9A6A;background-color:#E8F3E9;",
            "warning", CALLOUT_BASE + "border-left:4px solid #D4A017;background-color:#FDF6E3;",
            "danger", CALLOUT_BASE + "border-left:4px solid #C0392B;background-color:#FBEAEA;");

    private final SpringTemplateEngine engine;
    private final MailProperties properties;

    public ThymeleafEmailRenderer(SpringTemplateEngine engine, MailProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    public RenderedEmail render(ComposedEmail email) {
        Context context = new Context(MailLocales.toLocale(email.locale()));
        context.setVariable("email", email);
        context.setVariable("lang", MailLocales.ENGLISH.equals(email.locale()) ? "en" : "es");
        context.setVariable("brandName", properties.brandName());
        context.setVariable("supportEmail", properties.supportEmail());
        context.setVariable("calloutStyles", CALLOUT_STYLES);
        return new RenderedEmail(email.subject(), email.preheader(), engine.process(TEMPLATE, context), email.text());
    }
}
