package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.mail.domain.EmailBrand;
import com.ninsky.cronos.mail.domain.EmailSender;
import com.ninsky.cronos.mail.domain.EmailSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code app.mail.*}: the default sender and brand (served until an administrator saves the email settings), the
 * verified sending domains and the public image assets.
 *
 * @param messageIdDomain  right-hand side of {@code Message-ID} ({@code <eventId@domain>})
 * @param testSendsPerHour administrator test sends allowed per user and hour
 * @param tenantId         the tenant whose email settings and overrides this deployment serves (single-tenant today;
 *                         the JWT carries no tenant claim, so every request resolves to this id)
 * @param verifiedDomains  domains with SPF/DKIM aligned at the provider; a From outside them is refused. The domain of
 *                         {@code senderAddress} is always included
 */
@Validated
@ConfigurationProperties("app.mail")
public record MailProperties(
        @NotBlank @Email String senderAddress,
        @DefaultValue("Cronos Bakery") @NotBlank String senderName,
        String replyTo,
        @DefaultValue("Cronos") @NotBlank String brandName,
        @DefaultValue("soporte@cronos.mx") @NotBlank String supportEmail,
        @DefaultValue("cronos.mx") @NotBlank String messageIdDomain,
        @DefaultValue("20") int testSendsPerHour,
        @DefaultValue("00000000-0000-0000-0000-000000000000") UUID tenantId,
        @DefaultValue({}) List<String> verifiedDomains,
        @DefaultValue @Valid Brand brand,
        @DefaultValue @Valid Assets assets
) {

    /**
     * Default look of the emails.
     *
     * @param preferencesUrl empty → {@code app.frontend.url} + the notification preferences page
     */
    public record Brand(
            @DefaultValue("#10b981") String primaryColor,
            @DefaultValue("#f9a94c") String accentColor,
            @DefaultValue("") String logoUrl,
            @DefaultValue("Ciudad de México, México") String address,
            @DefaultValue("") String preferencesUrl
    ) {
    }

    /**
     * Uploaded email images ({@code POST /admin/email-templates/assets}).
     *
     * @param storage       {@code local} (filesystem, dev/test) or {@code gcs}
     * @param publicBaseUrl absolute, anonymous prefix of every asset URL (the CDN in prod); the object key is appended.
     *                      The default serves assets through the API's {@code /public/mail-assets/**}
     * @param enforceHost   true (prod): image URLs in templates and settings must start with {@code publicBaseUrl}
     */
    public record Assets(
            @DefaultValue("local") String storage,
            @DefaultValue("http://localhost:9191/api/v1/public/mail-assets") String publicBaseUrl,
            @DefaultValue("./uploads") String localDirectory,
            String gcsBucket,
            @DefaultValue("false") boolean enforceHost
    ) {

        /** Absolute URL of {@code objectKey}. */
        public String urlFor(String objectKey) {
            return publicBaseUrl.endsWith("/") ? publicBaseUrl + objectKey : publicBaseUrl + "/" + objectKey;
        }
    }

    /** Lower-cased verified domains, including the default sender's. */
    public Set<String> verifiedSendingDomains() {
        return Stream.concat(verifiedDomains.stream(), Stream.of(senderAddress.substring(senderAddress.indexOf('@') + 1)))
                .map(domain -> domain.strip().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The settings served before the first save ({@code revision} null). */
    public EmailSettings defaultSettings(String frontendUrl) {
        String preferences = brand.preferencesUrl().isBlank()
                ? frontendUrl.replaceAll("/+$", "") + "/cronos/notificaciones?tab=preferences" : brand.preferencesUrl();
        return new EmailSettings(
                new EmailBrand(brandName, brand.primaryColor(), brand.accentColor(), brand.logoUrl().isBlank() ? null : brand.logoUrl(),
                        null, supportEmail, brand.address(), preferences),
                new EmailSender(senderName, senderAddress, replyTo == null || replyTo.isBlank() ? null : replyTo),
                null);
    }
}
