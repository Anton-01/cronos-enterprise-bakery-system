package com.ninsky.cronos.mail.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailComposerTest {

    private static final TemplateDefinition TEMPLATE = new TemplateDefinition("test.welcome", EmailCategory.ACCOUNT, MailAudience.USER, true,
            "Test", new LocalizedText("Prueba", "Test"),
            List.of(new TemplateVariable("name", new LocalizedText("Nombre", "Name"), "Ana", VariableKind.TEXT),
                    new TemplateVariable("link", new LocalizedText("Enlace", "Link"), "https://x.mx", VariableKind.URL)),
            Map.of(EmailLocale.ES_MX, new LocalizedEmail("Hola {{ name }}", "Pre {{name}}", List.of(
                            new EmailBlock.Paragraph("Hola, {{ name }}. Soporte: {{ supportEmail }}"),
                            new EmailBlock.Button("Abrir", "{{ link }}"),
                            new EmailBlock.Details(List.of(new DetailRow("Nombre", "{{ name }}"), new DetailRow("Vacío", ""))),
                            new EmailBlock.Image("https://assets.cronos.mx/mail/a.png", "Pastel de {{ name }}", 200, "{{ link }}"),
                            new EmailBlock.Code("ABC-123", "Vence pronto"))),
                    EmailLocale.EN, new LocalizedEmail("Hi {{ name }}", "", List.of(new EmailBlock.Paragraph("Hi {{ name }}")))));

    private static final Map<String, String> IMPLICIT = Map.of("brandName", "Cronos", "supportEmail", "soporte@cronos.mx");

    @Test
    void appliesVariablesToSubjectPreheaderAndBlocks() {
        ComposedEmail email = EmailComposer.compose(TEMPLATE, "es-MX", Map.of("name", "Ana", "link", "https://app.cronos.mx/x"), IMPLICIT);
        assertThat(email.subject()).isEqualTo("Hola Ana");
        assertThat(email.preheader()).isEqualTo("Pre Ana");
        assertThat(email.locale()).isEqualTo("es-MX");
        assertThat(email.blocks().getFirst()).isEqualTo(new EmailBlock.Paragraph("Hola, Ana. Soporte: soporte@cronos.mx"));
        assertThat(email.blocks().get(1)).isEqualTo(new EmailBlock.Button("Abrir", "https://app.cronos.mx/x"));
        assertThat(((EmailBlock.Details) email.blocks().get(2)).rows().getFirst().value()).isEqualTo("Ana");
        assertThat(email.blocks().get(3)).isEqualTo(new EmailBlock.Image("https://assets.cronos.mx/mail/a.png", "Pastel de Ana", 200,
                "https://app.cronos.mx/x"));
        assertThat(email.text()).contains("Hola, Ana.").contains("Abrir: https://app.cronos.mx/x").contains("Nombre: Ana")
                .doesNotContain("Vacío").contains("[Pastel de Ana] (https://app.cronos.mx/x)").contains("    ABC-123\nVence pronto");
    }

    @Test
    void composesTheGivenContentInsteadOfTheDefault() {
        LocalizedEmail draft = new LocalizedEmail("Borrador {{ name }}", "", List.of(new EmailBlock.Heading("Hola {{ brandName }}")));
        ComposedEmail email = EmailComposer.compose(TEMPLATE, EmailLocale.ES_MX, draft, Map.of("name", "Ana", "link", "https://x.mx"), IMPLICIT);
        assertThat(email.subject()).isEqualTo("Borrador Ana");
        assertThat(email.text()).startsWith("HOLA CRONOS");
    }

    @Test
    void neutralisesDangerousLinks() {
        ComposedEmail email = EmailComposer.compose(TEMPLATE, "es-MX", Map.of("name", "Ana", "link", "javascript:alert(1)"), IMPLICIT);
        assertThat(((EmailBlock.Button) email.blocks().get(1)).url()).isEqualTo("#");
        assertThat(((EmailBlock.Image) email.blocks().get(3)).href()).isEqualTo("#");
    }

    @Test
    void englishTagsUseTheEnglishContentAndUnknownOnesSpanish() {
        assertThat(EmailComposer.compose(TEMPLATE, "en-US", Map.of("name", "Ana", "link", "https://x.mx"), IMPLICIT).subject())
                .isEqualTo("Hi Ana");
        assertThat(EmailComposer.compose(TEMPLATE, "fr", Map.of("name", "Ana", "link", "https://x.mx"), IMPLICIT).subject())
                .isEqualTo("Hola Ana");
    }

    @Test
    void aMissingDeclaredVariableIsAProgrammingError() {
        assertThatThrownBy(() -> EmailComposer.compose(TEMPLATE, "es-MX", Map.of("name", "Ana"), IMPLICIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("link");
    }

    @Test
    void placeholdersAreReplacedLiterally() {
        assertThat(Placeholders.apply("{{ a }} and {{b}}", Map.of("a", "$1 \\ x", "b", "y"))).isEqualTo("$1 \\ x and y");
        assertThat(Placeholders.referenced("{{ a }} {{ b_c }} { d }")).containsExactlyInAnyOrder("a", "b_c");
        assertThat(Placeholders.referencedLoosely("{{ a }} {{ user name }}")).containsExactlyInAnyOrder("a", "user name");
        assertThat(Placeholders.single(" {{ resetUrl }} ")).contains("resetUrl");
        assertThat(Placeholders.single("https://x/{{ resetUrl }}")).isEmpty();
    }

    @Test
    void localesHaveStrictAndLenientParsing() {
        assertThat(EmailLocale.fromTag("es-MX")).contains(EmailLocale.ES_MX);
        assertThat(EmailLocale.fromTag("es-mx")).isEmpty();
        assertThat(EmailLocale.fromTag("es")).isEmpty();
        assertThat(EmailLocale.normalize("en-GB")).isEqualTo(EmailLocale.EN);
        assertThat(EmailLocale.normalize(null)).isEqualTo(EmailLocale.ES_MX);
    }
}
