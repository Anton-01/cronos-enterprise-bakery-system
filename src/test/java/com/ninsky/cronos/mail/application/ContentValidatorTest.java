package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.mail.MailFixtures;
import com.ninsky.cronos.mail.domain.DetailRow;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.domain.TemplateDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** §3.3: limits per block type, link rules and declared placeholders, every problem reported with its path. */
class ContentValidatorTest {

    private static final TemplateDefinition RESET = MailFixtures.DEFAULTS.find("auth.password-reset").orElseThrow();
    private final ContentValidator validator = new ContentValidator(MailFixtures.properties(true));

    @Test
    void acceptsEveryBlockTypeWithinItsLimits() {
        assertThatCode(() -> validator.validate(RESET, content(
                new EmailBlock.Heading("Hola {{ displayName }}"),
                new EmailBlock.Paragraph("Escríbenos a {{ supportEmail }}"),
                new EmailBlock.Button("Elegir", "{{ resetUrl }}"),
                new EmailBlock.Button("Ayuda", "mailto:soporte@cronos.mx"),
                new EmailBlock.Code("ABC-123", null),
                new EmailBlock.Details(List.of(new DetailRow("Vence", "{{ expiresAt }}"))),
                new EmailBlock.Callout("danger", "Cuidado"),
                new EmailBlock.ListBlock(List.of("uno", "dos")),
                new EmailBlock.Image("https://assets.cronos.mx/mail/a.png", "", 120, "https://cronos.mx"),
                new EmailBlock.Divider(),
                new EmailBlock.Signature("El equipo de {{ brandName }}")))).doesNotThrowAnyException();
    }

    @Test
    void anUndeclaredPlaceholderIsUnknownVariableAtItsPath() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> validator.validate(RESET,
                new LocalizedEmail("Hola {{ nombre }}", "", List.of(new EmailBlock.Paragraph("ok"),
                        new EmailBlock.Details(List.of(new DetailRow("a", "{{ token }}"))), new EmailBlock.Paragraph("{{ user name }}")))));
        assertThat(error.violations()).extracting(ApiException.Violation::code, ApiException.Violation::field).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(ApiErrorCode.UNKNOWN_VARIABLE, "subject"),
                org.assertj.core.groups.Tuple.tuple(ApiErrorCode.UNKNOWN_VARIABLE, "blocks[1].rows[0].value"),
                org.assertj.core.groups.Tuple.tuple(ApiErrorCode.UNKNOWN_VARIABLE, "blocks[2].text"));
        assertThat(error.status().value()).isEqualTo(400);
    }

    @Test
    void linksMustBeHttpsMailtoOrOneUrlVariable() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> validator.validate(RESET, content(
                new EmailBlock.Button("a", "javascript:alert(1)"),
                new EmailBlock.Button("b", "http://cronos.mx"),
                new EmailBlock.Button("c", "{{ displayName }}"),
                new EmailBlock.Button("d", "{{ resetUrl }} {{ resetUrl }}"),
                new EmailBlock.Button("e", "{{ missing }}"),
                new EmailBlock.Image("https://assets.cronos.mx/mail/a.png", "x", null, "javascript:x"))));
        assertThat(error.violations()).extracting(ApiException.Violation::field).containsExactlyInAnyOrder(
                "blocks[0].url", "blocks[1].url", "blocks[2].url", "blocks[3].url", "blocks[4].url", "blocks[5].href");
        assertThat(error.violations()).filteredOn(v -> v.field().equals("blocks[4].url")).singleElement()
                .extracting(ApiException.Violation::code).isEqualTo(ApiErrorCode.UNKNOWN_VARIABLE);
    }

    @Test
    void imagesNeedHttpsOnTheAssetHostAnAltAndAWidthInRange() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> validator.validate(RESET, content(
                new EmailBlock.Image("http://assets.cronos.mx/mail/a.png", "x", null, null),
                new EmailBlock.Image("https://evil.example/a.png", "x", null, null),
                new EmailBlock.Image("https://assets.cronos.mx/mail/a.png", null, 20, null),
                new EmailBlock.Image("https://assets.cronos.mx/mail/a.png", "x", 537, null))));
        assertThat(error.violations()).extracting(ApiException.Violation::field, ApiException.Violation::messageKey).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("blocks[0].url", "mail.content.httpsOnly"),
                org.assertj.core.groups.Tuple.tuple("blocks[1].url", "mail.content.assetHost"),
                org.assertj.core.groups.Tuple.tuple("blocks[2].alt", "mail.content.required"),
                org.assertj.core.groups.Tuple.tuple("blocks[2].width", "mail.content.width"),
                org.assertj.core.groups.Tuple.tuple("blocks[3].width", "mail.content.width"));
        assertThatCode(() -> new ContentValidator(MailFixtures.properties(false)).validate(RESET,
                content(new EmailBlock.Image("https://cdn.example/a.png", "x", null, null)))).doesNotThrowAnyException();
    }

    @Test
    void lengthsCountsAndTonesAreChecked() {
        List<EmailBlock> tooMany = new ArrayList<>(Collections.nCopies(41, new EmailBlock.Divider()));
        ApiException count = catchThrowableOfType(ApiException.class, () -> validator.validate(RESET, new LocalizedEmail("s", "", tooMany)));
        assertThat(count.violations()).extracting(ApiException.Violation::field).containsExactly("blocks");

        ApiException error = catchThrowableOfType(ApiException.class, () -> validator.validate(RESET, new LocalizedEmail(" ", "x".repeat(201), Arrays.asList(
                new EmailBlock.Heading("x".repeat(201)),
                new EmailBlock.Callout("loud", "x"),
                new EmailBlock.ListBlock(List.of()),
                new EmailBlock.Details(Collections.nCopies(13, new DetailRow("a", "b"))),
                new EmailBlock.Code("x".repeat(65), "y".repeat(121)),
                new EmailBlock.Button("x".repeat(61), "https://cronos.mx"),
                null))));
        assertThat(error.violations()).extracting(ApiException.Violation::field).containsExactlyInAnyOrder("subject", "preheader",
                "blocks[0].text", "blocks[1].tone", "blocks[2].items", "blocks[3].rows", "blocks[4].text", "blocks[4].caption",
                "blocks[5].label", "blocks[6]");
    }

    private static LocalizedEmail content(EmailBlock... blocks) {
        return new LocalizedEmail("Restablece tu contraseña", "", List.of(blocks));
    }
}
