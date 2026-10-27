package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.mail.MailFixtures;
import com.ninsky.cronos.mail.application.ContentValidator;
import com.ninsky.cronos.mail.application.RenderedEmail;
import com.ninsky.cronos.mail.domain.ComposedEmail;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailBrand;
import com.ninsky.cronos.mail.domain.EmailCategory;
import com.ninsky.cronos.mail.domain.EmailComposer;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.Placeholders;
import com.ninsky.cronos.mail.domain.TemplateDefaults;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * §7.1/§11.1: every built-in template renders in both locales with its samples, nothing left unreplaced, links safe;
 * every default also passes the edit validation (so "save equal to the default" always round-trips).
 */
class EmailCatalogRenderingTest {

    private static final TemplateDefaults CATALOG = MailFixtures.DEFAULTS;
    private static final EmailSettings SETTINGS = MailFixtures.settings();
    private static final ThymeleafEmailRenderer RENDERER = new ThymeleafEmailRenderer(MailFixtures.engine());
    private static final ContentValidator VALIDATOR = new ContentValidator(MailFixtures.properties(true));

    @Test
    void theCatalogDeclaresTheDocumentedTemplatesWithTheirCategories() {
        assertThat(CATALOG.version()).isEqualTo("2026-10-24");
        assertThat(CATALOG.byKey()).containsKeys("auth.registration-received", "auth.email-verification", "admin.registration-pending",
                "auth.account-approved", "auth.account-rejected", "auth.password-reset", "auth.password-changed", "auth.account-locked",
                "auth.new-sign-in", "quote.sent-to-client", "quote.accepted-baker", "quote.accepted-client", "quote.expiring-reminder",
                "calendar.order-reminder", "calendar.order-scheduled-client");
        assertThat(CATALOG.byKey().get("auth.invitation").category()).isEqualTo(EmailCategory.ACCOUNT);
        assertThat(CATALOG.byKey().get("auth.password-reset").category()).isEqualTo(EmailCategory.SECURITY);
        assertThat(CATALOG.byKey().get("admin.registration-pending").category()).isEqualTo(EmailCategory.ADMIN);
        assertThat(CATALOG.byKey().get("quote.expiring-reminder").category()).isEqualTo(EmailCategory.QUOTES);
        assertThat(CATALOG.byKey().get("calendar.order-reminder").category()).isEqualTo(EmailCategory.ORDERS);
        assertThat(CATALOG.byKey().get("recipe.shared").category()).isEqualTo(EmailCategory.RECIPES);
    }

    @TestFactory
    Stream<DynamicTest> everyTemplateRendersInEveryLocale() {
        return CATALOG.templates().stream().flatMap(template -> Arrays.stream(EmailLocale.values())
                .map(locale -> DynamicTest.dynamicTest(template.key() + " / " + locale, () -> render(template, locale))));
    }

    @TestFactory
    Stream<DynamicTest> everyDefaultPassesTheEditValidation() {
        return CATALOG.templates().stream().flatMap(template -> Arrays.stream(EmailLocale.values())
                .map(locale -> DynamicTest.dynamicTest(template.key() + " / " + locale,
                        () -> assertThatCode(() -> VALIDATOR.validate(template, template.content(locale))).doesNotThrowAnyException())));
    }

    private static void render(TemplateDefinition template, EmailLocale locale) {
        ComposedEmail email = EmailComposer.compose(template, locale, template.content(locale), template.samples(), SETTINGS.implicitVariables());
        RenderedEmail rendered = RENDERER.render(email, SETTINGS);
        assertThat(rendered.subject()).isNotBlank().doesNotContain("{{");
        assertThat(rendered.html()).doesNotContain("{{").contains(SETTINGS.brand().name())
                .contains("lang=\"" + (locale == EmailLocale.EN ? "en" : "es") + "\"");
        assertThat(rendered.text()).doesNotContain("{{").isNotBlank();
        assertThat(Placeholders.hasPlaceholder(rendered.preheader())).isFalse();
    }

    @Test
    void valuesAreEscapedAndUnsafeLinksNeutralised() {
        TemplateDefinition reset = CATALOG.find("auth.password-reset").orElseThrow();
        Map<String, String> variables = new HashMap<>(reset.samples());
        variables.put("displayName", "<script>alert(1)</script>");
        variables.put("resetUrl", "javascript:alert(1)");
        RenderedEmail rendered = RENDERER.render(EmailComposer.compose(reset, "es-MX", variables, SETTINGS.implicitVariables()), SETTINGS);
        assertThat(rendered.html()).doesNotContain("<script>").contains("&lt;script&gt;").doesNotContain("javascript:");
    }

    @Test
    void theBrandDrivesColorsLogoBannerAndFooter() {
        TemplateDefinition reset = CATALOG.find("auth.password-reset").orElseThrow();
        EmailBrand plain = SETTINGS.brand();
        RenderedEmail withoutBanner = RENDERER.render(compose(reset), SETTINGS);
        assertThat(withoutBanner.html()).contains("height:4px;line-height:4px;font-size:4px;background-color:#10b981;")
                .contains("background-color:#10b981;").contains("Ciudad de México, México").doesNotContain("Preferencias de correo");

        EmailSettings branded = new EmailSettings(new EmailBrand("Dulce", "#123abc", "#abcdef", "https://assets.cronos.mx/mail/logo.png",
                "https://assets.cronos.mx/mail/banner.png", plain.supportEmail(), plain.address(), plain.preferencesUrl()), SETTINGS.sender(), 3L);
        String html = RENDERER.render(compose(reset), branded).html();
        assertThat(html).contains("src=\"https://assets.cronos.mx/mail/banner.png\"").contains("width=\"600\"")
                .doesNotContain("height:4px;line-height:4px").contains("src=\"https://assets.cronos.mx/mail/logo.png\"")
                .contains("background-color:#123abc;").contains("border-bottom:2px solid #abcdef;");
    }

    @Test
    void imagesAreCenteredSizedAndLinkedAndNonTransactionalMailLinksThePreferences() {
        TemplateDefinition pending = CATALOG.find("admin.registration-pending").orElseThrow();
        LocalizedEmail content = new LocalizedEmail("Hola", "", List.of(
                new EmailBlock.Image("https://assets.cronos.mx/mail/a.png", "Pastel", 240, "https://cronos.mx"),
                new EmailBlock.Image("https://assets.cronos.mx/mail/b.png", "", null, null),
                new EmailBlock.Code("ABC", "Tu código")));
        ComposedEmail email = EmailComposer.compose(pending, EmailLocale.ES_MX, content, pending.samples(), SETTINGS.implicitVariables());
        String html = RENDERER.render(email, SETTINGS).html();
        assertThat(html).contains("<a href=\"https://cronos.mx\"").contains("alt=\"Pastel\"").contains("width=\"240\"")
                .contains("max-width:240px;height:auto;border:0;border-radius:10px;").contains("width=\"536\"")
                .contains("Tu código").contains("Preferencias de correo").contains(SETTINGS.brand().preferencesUrl());
        assertThat(email.text()).contains("[Pastel] (https://cronos.mx)").doesNotContain("[]");
    }

    private static ComposedEmail compose(TemplateDefinition template) {
        return EmailComposer.compose(template, "es-MX", template.samples(), SETTINGS.implicitVariables());
    }
}
