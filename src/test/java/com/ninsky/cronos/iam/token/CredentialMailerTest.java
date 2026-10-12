package com.ninsky.cronos.iam.token;

import com.ninsky.cronos.mail.application.MailRequest;
import com.ninsky.cronos.mail.application.Mailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialMailerTest {

    private static final UUID USER = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");
    private static final String RAW = "a".repeat(43);

    private final Mailer mail = mock(Mailer.class);
    private final CredentialMailer mailer = new CredentialMailer(mail, new NoOpTransactions(), "https://app.cronos.mx");

    private static CredentialDelivery invitation(String locale) {
        return new CredentialDelivery.Invitation(USER, "ana@cronos.mx", "Ana", locale, RAW, Instant.parse("2026-10-08T18:00:00Z"));
    }

    @Test
    void invitationQueuesTheCatalogTemplateWithTheActivationLink() {
        MailRequest english = mailer.compose(invitation("en-US"));
        assertThat(english.templateKey()).isEqualTo("auth.invitation");
        assertThat(english.locale()).isEqualTo("en");
        assertThat(english.to()).isEqualTo("ana@cronos.mx");
        assertThat(english.variables()).containsEntry("activationUrl", "https://app.cronos.mx/auth/activate?token=" + RAW)
                .containsEntry("displayName", "Ana")
                .containsKey("expiresAt");

        assertThat(mailer.compose(invitation(null)).locale()).isEqualTo("es-MX");
    }

    @Test
    void temporaryPasswordLinksToSignIn() {
        MailRequest request = mailer.compose(new CredentialDelivery.TemporaryPassword(USER, "a@b.mx", "Ana", "es", "Tmp-Secret-123!"));
        assertThat(request.templateKey()).isEqualTo("auth.temporary-password");
        assertThat(request.variables()).containsEntry("signInUrl", "https://app.cronos.mx/auth/login")
                .containsEntry("temporaryPassword", "Tmp-Secret-123!");
    }

    @Test
    void securityNoticesMapToTheirTemplates() {
        assertThat(mailer.compose(new CredentialDelivery.SecurityNotice(USER, "a@b.mx", "Ana", "es", "recoveryCodeUsed", 7)).variables())
                .containsEntry("remainingCodes", "7");
        assertThat(mailer.compose(new CredentialDelivery.SecurityNotice(USER, "a@b.mx", "Ana", "es", "twoFactorDisabled")).templateKey())
                .isEqualTo("auth.two-factor-disabled");
        assertThatThrownBy(() -> mailer.compose(new CredentialDelivery.SecurityNotice(USER, "a@b.mx", "Ana", "es", "unknown")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queuesTheEmailForTheAccount() {
        when(mail.send(any())).thenReturn(Optional.of(UUID.randomUUID()));
        mailer.on(invitation("es"));
        ArgumentCaptor<MailRequest> captor = ArgumentCaptor.forClass(MailRequest.class);
        verify(mail).send(captor.capture());
        assertThat(captor.getValue().recipientUserId()).isEqualTo(USER);
    }

    @Test
    void rawSecretsNeverAppearInToString() {
        assertThat(invitation("es").toString()).doesNotContain(RAW);
        assertThat(mailer.compose(invitation("es")).toString()).doesNotContain(RAW);
    }

    /** Runs the callback without a database. */
    static final class NoOpTransactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
