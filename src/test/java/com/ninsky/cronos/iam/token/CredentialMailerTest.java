package com.ninsky.cronos.iam.token;

import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CredentialMailerTest {

    private static final UUID USER = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");
    private static final String RAW = "a".repeat(43);

    private final MailService mail = mock(MailService.class);
    private final AuditRecorder recorder = mock(AuditRecorder.class);
    private final CredentialMailer mailer = new CredentialMailer(mail, messages(), recorder, "https://app.cronos.mx", 3, 0);

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("i18n/security");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static CredentialDelivery invitation(String locale) {
        return new CredentialDelivery.Invitation(USER, "ana@cronos.mx", "Ana", locale, RAW, Instant.parse("2026-10-08T18:00:00Z"));
    }

    @Test
    void composesALocalisedInvitationWithTheActivationLink() {
        EmailRequest english = mailer.compose(invitation("en-US"), CredentialMailer.localeOf("en-US"));
        assertThat(english.to()).isEqualTo("ana@cronos.mx");
        assertThat(english.subject()).isEqualTo("You have been invited to Cronos");
        assertThat(english.templateName()).isEqualTo(CredentialMailer.TEMPLATE);
        assertThat(english.variables()).containsEntry("link", "https://app.cronos.mx/auth/activate?token=" + RAW)
                .containsEntry("greeting", "Hello Ana,");

        EmailRequest spanish = mailer.compose(invitation(null), CredentialMailer.localeOf(null));
        assertThat(spanish.subject()).isEqualTo("Te invitaron a Cronos");
        assertThat((String) spanish.variables().get("expiry")).startsWith("Este enlace vence el");
    }

    @Test
    void temporaryPasswordLinksToSignIn() {
        EmailRequest request = mailer.compose(new CredentialDelivery.TemporaryPassword(USER, "a@b.mx", "Ana", "es", "Tmp-Secret-123!"),
                Locale.forLanguageTag("es-MX"));
        assertThat(request.variables()).containsEntry("link", "https://app.cronos.mx/auth/login")
                .containsEntry("secret", "Tmp-Secret-123!");
    }

    @Test
    void retriesTransientFailures() {
        doThrow(new MailSendException("down")).doNothing().when(mail).sendNow(any(), any());
        assertThat(mailer.deliver(invitation("es"))).isTrue();
        verify(mail, times(2)).sendNow(any(), any());
        verify(recorder, never()).recordIndependently(any());
    }

    @Test
    void finalFailureIsAuditedAndNeverThrown() {
        doThrow(new MailSendException("down")).when(mail).sendNow(any(), any());
        assertThat(mailer.deliver(invitation("es"))).isFalse();
        verify(mail, times(3)).sendNow(any(), any());

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(recorder).recordIndependently(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.INVITATION_DELIVERY_FAILED);
        assertThat(event.getValue().outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(event.getValue().severity()).isEqualTo(AuditSeverity.WARNING);
        assertThat(event.getValue().params()).containsEntry("detail", "invitation").doesNotContainValue(RAW);
    }

    @Test
    void auditFailureIsSwallowedToo() {
        doThrow(new MailSendException("down")).when(mail).sendNow(any(), any());
        doThrow(new IllegalStateException("db down")).when(recorder).recordIndependently(any());
        assertThat(mailer.deliver(invitation("es"))).isFalse();
    }
}
