package com.ninsky.cronos.iam.token;

import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.mail.application.MailRequest;
import com.ninsky.cronos.mail.application.Mailer;
import com.ninsky.cronos.mail.domain.MailLocales;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Map;

/**
 * Turns {@link CredentialDelivery} notices into catalog emails queued in the issuing transaction (transactional
 * outbox): the email exists if and only if the token, password or change that it announces was committed, survives a
 * restart, and is retried by the outbox instead of a sleeping thread. Raw secrets travel only inside the encrypted
 * {@code mail.requested} payload and are never logged.
 */
@Component
public class CredentialMailer {

    private final Mailer mailer;
    private final TransactionTemplate transaction;
    private final String frontendUrl;

    public CredentialMailer(Mailer mailer, PlatformTransactionManager transactions, @Value("${app.frontend.url}") String frontendUrl) {
        this.mailer = mailer;
        this.transaction = new TransactionTemplate(transactions);
        this.frontendUrl = frontendUrl;
    }

    /** Synchronous: joins the publisher's transaction (or opens one for a caller without it). */
    @EventListener
    public void on(CredentialDelivery delivery) {
        MailRequest request = compose(delivery).forUser(delivery.userId());
        transaction.executeWithoutResult(status -> mailer.send(request));
    }

    /** The catalog email of {@code delivery}. */
    MailRequest compose(CredentialDelivery delivery) {
        String locale = MailLocales.normalize(delivery.locale());
        String name = delivery.displayName();
        return switch (delivery) {
            case CredentialDelivery.Invitation d -> MailRequest.of("auth.invitation", locale, d.email(), Map.of(
                    "displayName", name, "activationUrl", link("/auth/activate", d.rawToken()), "expiresAt", format(d.expiresAt(), locale)));
            case CredentialDelivery.PasswordResetLink d -> MailRequest.of("auth.password-reset", locale, d.email(), Map.of(
                    "displayName", name, "resetUrl", link("/auth/reset-password", d.rawToken()), "expiresAt", format(d.expiresAt(), locale)));
            case CredentialDelivery.EmailVerification d -> MailRequest.of("auth.email-verification", locale, d.email(), Map.of(
                    "displayName", name, "verifyUrl", link("/auth/verify-email", d.rawToken()), "expiresAt", format(d.expiresAt(), locale)));
            case CredentialDelivery.TemporaryPassword d -> MailRequest.of("auth.temporary-password", locale, d.email(), Map.of(
                    "displayName", name, "temporaryPassword", d.rawPassword(), "signInUrl", frontendUrl + "/auth/login"));
            case CredentialDelivery.EmailChangedNotice d -> MailRequest.of("auth.email-changed", locale, d.email(), Map.of(
                    "displayName", name, "newEmail", d.newEmailMasked()));
            case CredentialDelivery.SecurityNotice d -> securityNotice(d, locale);
        };
    }

    private MailRequest securityNotice(CredentialDelivery.SecurityNotice notice, String locale) {
        return switch (notice.kind()) {
            case "recoveryCodeUsed" -> MailRequest.of("auth.recovery-code-used", locale, notice.email(), Map.of(
                    "displayName", notice.displayName(),
                    "remainingCodes", notice.args() == null || notice.args().length == 0 ? "" : String.valueOf(notice.args()[0])));
            case "twoFactorDisabled" -> MailRequest.of("auth.two-factor-disabled", locale, notice.email(), Map.of(
                    "displayName", notice.displayName()));
            default -> throw new IllegalArgumentException("Unknown security notice " + notice.kind());
        };
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(frontendUrl).path(path).queryParam("token", token).build().toUriString();
    }

    static String format(Instant instant, String locale) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(MailLocales.toLocale(locale)).withZone(TenantTime.ZONE).format(instant);
    }
}
