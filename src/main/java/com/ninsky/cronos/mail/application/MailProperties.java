package com.ninsky.cronos.mail.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.mail.*}: sender identity and the implicit template variables.
 *
 * @param messageIdDomain right-hand side of {@code Message-ID} ({@code <eventId@domain>})
 * @param testSendsPerHour administrator test sends allowed per user and hour
 */
@Validated
@ConfigurationProperties("app.mail")
public record MailProperties(
        @NotBlank @Email String senderAddress,
        @DefaultValue("Cronos Bakery") @NotBlank String senderName,
        @DefaultValue("Cronos") @NotBlank String brandName,
        @DefaultValue("soporte@cronos.mx") @NotBlank String supportEmail,
        @DefaultValue("cronos.mx") @NotBlank String messageIdDomain,
        @DefaultValue("20") int testSendsPerHour
) {
}
