package com.ninsky.cronos.iam.token;

import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sends {@link CredentialDelivery} emails once the issuing transaction committed, off the request
 * thread, in the recipient's locale. Retries a few times; a final failure is audited as
 * {@code INVITATION_DELIVERY_FAILED} and never propagates. Raw secrets are never logged.
 */
@Slf4j
@Component
public class CredentialMailer {

    static final String TEMPLATE = "auth/credential";

    private final MailService mail;
    private final MessageSource messages;
    private final AuditRecorder recorder;
    private final String frontendUrl;
    private final int maxAttempts;
    private final long retryDelayMillis;

    public CredentialMailer(MailService mail, MessageSource messages, AuditRecorder recorder,
                            @Value("${app.frontend.url}") String frontendUrl,
                            @Value("${app.security.mail.max-attempts:3}") int maxAttempts,
                            @Value("${app.security.mail.retry-delay-ms:2000}") long retryDelayMillis) {
        this.mail = mail;
        this.messages = messages;
        this.recorder = recorder;
        this.frontendUrl = frontendUrl;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMillis = retryDelayMillis;
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(CredentialDelivery delivery) {
        deliver(delivery);
    }

    /** @return true when the message was handed to the mail server */
    boolean deliver(CredentialDelivery delivery) {
        Locale locale = localeOf(delivery.locale());
        EmailRequest request;
        try {
            request = compose(delivery, locale);
        } catch (RuntimeException e) {
            return failed(delivery, e);
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                mail.sendNow(request, locale);
                log.info("Sent {} email to user {}", kind(delivery), delivery.userId());
                return true;
            } catch (RuntimeException e) {
                log.warn("Attempt {}/{} to send {} email to user {} failed: {}", attempt, maxAttempts, kind(delivery),
                        delivery.userId(), e.getMessage());
                if (attempt == maxAttempts || !pause(attempt)) {
                    return failed(delivery, e);
                }
            }
        }
        return false;
    }

    EmailRequest compose(CredentialDelivery delivery, Locale locale) {
        String kind = kind(delivery);
        Map<String, Object> variables = new HashMap<>();
        variables.put("lang", locale.getLanguage());
        variables.put("greeting", text("mail.greeting", locale, delivery.displayName()));
        variables.put("label", text("mail." + kind + ".label", locale));
        variables.put("heading", text("mail." + kind + ".heading", locale));
        variables.put("footer", text("mail.footer", locale));
        variables.put("fallback", text("mail.fallback", locale));
        variables.put("ignore", text("mail.ignore", locale));
        switch (delivery) {
            case CredentialDelivery.Invitation d -> link(variables, kind, locale, "/auth/activate", d.rawToken(), d.expiresAt());
            case CredentialDelivery.PasswordResetLink d -> link(variables, kind, locale, "/auth/reset-password", d.rawToken(), d.expiresAt());
            case CredentialDelivery.EmailVerification d -> link(variables, kind, locale, "/auth/verify-email", d.rawToken(), d.expiresAt());
            case CredentialDelivery.TemporaryPassword d -> {
                variables.put("body", text("mail." + kind + ".body", locale));
                variables.put("secretLabel", text("mail." + kind + ".secretLabel", locale));
                variables.put("secret", d.rawPassword());
                variables.put("cta", text("mail." + kind + ".cta", locale));
                variables.put("link", frontendUrl + "/auth/login");
            }
            case CredentialDelivery.EmailChangedNotice d ->
                    variables.put("body", text("mail." + kind + ".body", locale, d.newEmailMasked()));
        }
        return EmailRequest.builder().to(delivery.email()).subject(text("mail." + kind + ".subject", locale))
                .templateName(TEMPLATE).variables(variables).build();
    }

    static String kind(CredentialDelivery delivery) {
        return switch (delivery) {
            case CredentialDelivery.Invitation ignored -> "invitation";
            case CredentialDelivery.TemporaryPassword ignored -> "temporaryPassword";
            case CredentialDelivery.PasswordResetLink ignored -> "passwordReset";
            case CredentialDelivery.EmailVerification ignored -> "emailVerification";
            case CredentialDelivery.EmailChangedNotice ignored -> "emailChanged";
        };
    }

    static Locale localeOf(String tag) {
        return tag != null && tag.toLowerCase(Locale.ROOT).startsWith("en") ? Locale.ENGLISH : Locale.forLanguageTag("es-MX");
    }

    private void link(Map<String, Object> variables, String kind, Locale locale, String path, String token, Instant expiresAt) {
        variables.put("body", text("mail." + kind + ".body", locale));
        variables.put("cta", text("mail." + kind + ".cta", locale));
        variables.put("link", UriComponentsBuilder.fromUriString(frontendUrl).path(path).queryParam("token", token).build().toUriString());
        variables.put("expiry", text("mail.expiry", locale, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(locale).withZone(TenantTime.ZONE).format(expiresAt)));
    }

    private boolean failed(CredentialDelivery delivery, Exception cause) {
        log.error("Giving up on {} email to user {}: {}", kind(delivery), delivery.userId(), cause.getMessage());
        try {
            recorder.recordIndependently(AuditEvent.of(AuditAction.INVITATION_DELIVERY_FAILED, AuditTargets.USER,
                            delivery.userId(), delivery.displayName())
                    .outcome(AuditOutcome.FAILURE)
                    .severity(AuditSeverity.WARNING)
                    .params(Map.of("detail", kind(delivery), "attempts", maxAttempts))
                    .build());
        } catch (RuntimeException e) {
            log.error("Could not audit the failed {} email for user {}: {}", kind(delivery), delivery.userId(), e.getMessage());
        }
        return false;
    }

    private boolean pause(int attempt) {
        try {
            Thread.sleep(retryDelayMillis * attempt);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
