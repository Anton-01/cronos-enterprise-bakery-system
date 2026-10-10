package com.ninsky.cronos.mail.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.mail.application.MailProperties;
import com.ninsky.cronos.mail.application.RenderedEmail;
import com.ninsky.cronos.mail.domain.ComposedEmail;
import com.ninsky.cronos.mail.domain.EmailComposer;
import com.ninsky.cronos.mail.domain.EmailTemplate;
import com.ninsky.cronos.mail.domain.EmailTemplateCatalog;
import com.ninsky.cronos.mail.domain.MailLocales;
import com.ninsky.cronos.mail.domain.Placeholders;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** §7.1/§11.1: every catalog template renders in both locales with its samples, nothing left unreplaced, links safe. */
class EmailCatalogRenderingTest {

    private static final EmailTemplateCatalog CATALOG = new MailConfig().emailTemplateCatalog(new ObjectMapper());
    private static final MailProperties PROPERTIES = new MailProperties("no-reply@cronos.mx", "Cronos Bakery", "Cronos",
            "soporte@cronos.mx", "cronos.mx", 20);
    private static final ThymeleafEmailRenderer RENDERER = new ThymeleafEmailRenderer(engine(), PROPERTIES);

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    @Test
    void theCatalogDeclaresTheDocumentedTemplates() {
        assertThat(CATALOG.byKey()).containsKeys("auth.registration-received", "auth.email-verification", "admin.registration-pending",
                "auth.account-approved", "auth.account-rejected", "auth.password-reset", "auth.password-changed", "auth.account-locked",
                "auth.new-sign-in", "quote.sent-to-client", "quote.accepted-baker", "quote.accepted-client", "quote.expiring-reminder",
                "calendar.order-reminder", "calendar.order-scheduled-client");
    }

    @TestFactory
    Stream<DynamicTest> everyTemplateRendersInEveryLocale() {
        return CATALOG.templates().stream().flatMap(template -> MailLocales.ALL.stream()
                .map(locale -> DynamicTest.dynamicTest(template.key() + " / " + locale, () -> render(template, locale))));
    }

    private static void render(EmailTemplate template, String locale) {
        ComposedEmail email = EmailComposer.compose(template, locale, template.samples(),
                Map.of("brandName", PROPERTIES.brandName(), "supportEmail", PROPERTIES.supportEmail()));
        RenderedEmail rendered = RENDERER.render(email);
        assertThat(rendered.subject()).isNotBlank().doesNotContain("{{");
        assertThat(rendered.html()).doesNotContain("{{").contains(PROPERTIES.brandName());
        assertThat(rendered.text()).doesNotContain("{{").isNotBlank();
        assertThat(Placeholders.hasPlaceholder(rendered.preheader())).isFalse();
    }

    @Test
    void valuesAreEscapedAndUnsafeLinksNeutralised() {
        EmailTemplate reset = CATALOG.find("auth.password-reset").orElseThrow();
        Map<String, String> variables = new HashMap<>(reset.samples());
        variables.put("displayName", "<script>alert(1)</script>");
        variables.put("resetUrl", "javascript:alert(1)");
        RenderedEmail rendered = RENDERER.render(EmailComposer.compose(reset, "es-MX", variables,
                Map.of("brandName", "Cronos", "supportEmail", "soporte@cronos.mx")));
        assertThat(rendered.html()).doesNotContain("<script>").contains("&lt;script&gt;").doesNotContain("javascript:");
    }
}
