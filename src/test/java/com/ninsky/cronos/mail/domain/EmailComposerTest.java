package com.ninsky.cronos.mail.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailComposerTest {

    private static final EmailTemplate TEMPLATE = new EmailTemplate("test.welcome", MailAudience.USER, true, "Test", "Test",
            List.of(new TemplateVariable("name", "Name", "Ana", VariableKind.TEXT),
                    new TemplateVariable("link", "Link", "https://x.mx", VariableKind.URL)),
            Map.of("es-MX", new LocalizedEmail("Hola {{ name }}", "Pre {{name}}", List.of(
                            new EmailBlock(BlockType.PARAGRAPH, "Hola, {{ name }}. Soporte: {{ supportEmail }}", null, null, null, null, null),
                            new EmailBlock(BlockType.BUTTON, null, "Abrir", "{{ link }}", null, null, null),
                            new EmailBlock(BlockType.DETAILS, null, null, null, null, null, List.of(new DetailRow("Nombre", "{{ name }}"))))),
                    "en", new LocalizedEmail("Hi {{ name }}", "", List.of(
                            new EmailBlock(BlockType.PARAGRAPH, "Hi {{ name }}", null, null, null, null, null)))));

    private static final Map<String, String> IMPLICIT = Map.of("brandName", "Cronos", "supportEmail", "soporte@cronos.mx");

    @Test
    void appliesVariablesToSubjectPreheaderAndBlocks() {
        ComposedEmail email = EmailComposer.compose(TEMPLATE, "es-MX", Map.of("name", "Ana", "link", "https://app.cronos.mx/x"), IMPLICIT);
        assertThat(email.subject()).isEqualTo("Hola Ana");
        assertThat(email.preheader()).isEqualTo("Pre Ana");
        assertThat(email.blocks().getFirst().text()).isEqualTo("Hola, Ana. Soporte: soporte@cronos.mx");
        assertThat(email.blocks().get(1).url()).isEqualTo("https://app.cronos.mx/x");
        assertThat(email.blocks().get(2).rows().getFirst().value()).isEqualTo("Ana");
        assertThat(email.text()).contains("Hola, Ana.").contains("Abrir: https://app.cronos.mx/x").contains("Nombre: Ana");
    }

    @Test
    void neutralisesDangerousLinks() {
        ComposedEmail email = EmailComposer.compose(TEMPLATE, "es-MX", Map.of("name", "Ana", "link", "javascript:alert(1)"), IMPLICIT);
        assertThat(email.blocks().get(1).url()).isEqualTo("#");
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
    }
}
