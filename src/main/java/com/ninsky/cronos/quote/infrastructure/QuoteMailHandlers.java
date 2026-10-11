package com.ninsky.cronos.quote.infrastructure;

import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.quote.QuoteRepositoryPort;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.eventing.application.OutboxHandlers;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.mail.application.MailRequest;
import com.ninsky.cronos.mail.application.MailFormats;
import com.ninsky.cronos.mail.application.Mailer;
import com.ninsky.cronos.mail.domain.MailLocales;
import com.ninsky.cronos.quote.domain.QuoteActorType;
import com.ninsky.cronos.quote.domain.QuoteEvents;
import com.ninsky.cronos.quote.domain.ShareChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Optional;

/**
 * Emails of the quote lifecycle, each an independent outbox consumer: the quote to the client, the client's acceptance
 * to the baker, the confirmation to the client and the expiry reminder to the baker. Clients have no account, so their
 * emails use the baker's language.
 */
@Configuration
public class QuoteMailHandlers {

    private final QuoteRepositoryPort quotes;
    private final UserReadCustomRepository users;
    private final UserProfileRepositoryPort profiles;
    private final Mailer mailer;
    private final String appUrl;
    private final String publicQuoteUrl;

    public QuoteMailHandlers(QuoteRepositoryPort quotes, UserReadCustomRepository users, UserProfileRepositoryPort profiles, Mailer mailer,
                             @Value("${app.frontend.url}") String appUrl,
                             @Value("${app.frontend.urlSharePublicQuote}") String publicQuoteUrl) {
        this.quotes = quotes;
        this.users = users;
        this.profiles = profiles;
        this.mailer = mailer;
        this.appUrl = appUrl;
        this.publicQuoteUrl = publicQuoteUrl;
    }

    /** {@code quote.sent-to-client} when the quote is shared by email. */
    @Bean
    OutboxHandler<QuoteEvents.QuoteSent> quoteToClientMail() {
        return OutboxHandlers.of(QuoteEvents.QuoteSent.class, "QuoteToClientMail", (event, envelope) -> {
            if (event.channel() != ShareChannel.EMAIL) {
                return;
            }
            quote(event.quoteId()).filter(quote -> hasEmail(quote.getClientEmail())).ifPresent(quote -> {
                String locale = bakerLocale(quote);
                mailer.send(MailRequest.of("quote.sent-to-client", locale, quote.getClientEmail(), Map.of(
                        "clientName", quote.getClientName(),
                        "bakerName", bakerBusinessName(quote),
                        "quoteNumber", quote.getQuoteNumber(),
                        "total", MailFormats.money(quote.getTotal(), quote.getCurrency(), locale),
                        "validUntil", quote.getValidUntil() == null ? "" : MailFormats.date(quote.getValidUntil().toLocalDate(), locale),
                        "quoteUrl", publicQuoteUrl + quote.getPublicToken())));
            });
        });
    }

    /** {@code quote.accepted-baker} when the client accepted from the public link (with a link to schedule it). */
    @Bean
    OutboxHandler<QuoteEvents.QuoteAccepted> clientAcceptedMail() {
        return OutboxHandlers.of(QuoteEvents.QuoteAccepted.class, "ClientAcceptedMail", (event, envelope) -> {
            if (event.actor() != QuoteActorType.CLIENT) {
                return;
            }
            users.find(event.ownerId()).ifPresent(baker -> mailer.send(MailRequest.of("quote.accepted-baker", baker.locale(), baker.email(),
                    Map.of("bakerName", baker.displayName(),
                            "clientName", event.actorName() == null ? "" : event.actorName(),
                            "quoteNumber", event.quoteNumber(),
                            "total", MailFormats.money(event.total(), event.currency(), baker.locale()),
                            "comment", event.comment() == null ? "" : event.comment(),
                            "scheduleUrl", appUrl + "/cronos/cotizaciones/detalles/" + event.quoteId() + "?schedule=1"))
                    .forUser(baker.id())));
        });
    }

    /** {@code quote.accepted-client}: confirmation to the client when the quote has an email. */
    @Bean
    OutboxHandler<QuoteEvents.QuoteAccepted> acceptedConfirmationMail() {
        return OutboxHandlers.of(QuoteEvents.QuoteAccepted.class, "AcceptedConfirmationMail", (event, envelope) ->
                quote(event.quoteId()).filter(quote -> hasEmail(quote.getClientEmail())).ifPresent(quote -> {
                    String locale = bakerLocale(quote);
                    mailer.send(MailRequest.of("quote.accepted-client", locale, quote.getClientEmail(), Map.of(
                            "clientName", quote.getClientName(),
                            "bakerName", bakerBusinessName(quote),
                            "quoteNumber", quote.getQuoteNumber(),
                            "total", MailFormats.money(quote.getTotal(), quote.getCurrency(), locale),
                            "eventDate", quote.getEventDate() == null ? "" : MailFormats.date(quote.getEventDate(), locale))));
                }));
    }

    /** {@code quote.expiring-reminder} to the baker (the event is already once per quote). */
    @Bean
    OutboxHandler<QuoteEvents.QuoteExpiring> expiringReminderMail() {
        return OutboxHandlers.of(QuoteEvents.QuoteExpiring.class, "QuoteExpiringMail", (event, envelope) ->
                quote(event.quoteId()).filter(quote -> quote.getStatus().open()).ifPresent(quote ->
                        users.find(quote.getUserId()).ifPresent(baker -> mailer.send(MailRequest.of("quote.expiring-reminder", baker.locale(),
                                baker.email(), Map.of(
                                        "bakerName", baker.displayName(),
                                        "clientName", quote.getClientName(),
                                        "quoteNumber", quote.getQuoteNumber(),
                                        "validUntil", MailFormats.date(event.validUntil(), baker.locale()),
                                        "quoteUrl", appUrl + "/cronos/cotizaciones/detalles/" + quote.getId()))
                                .forUser(baker.id())))));
    }

    private Optional<Quote> quote(java.util.UUID id) {
        return quotes.findById(id).filter(quote -> quote.getDeletedAt() == null);
    }

    private String bakerLocale(Quote quote) {
        return users.find(quote.getUserId()).map(UserRow::locale).orElse(MailLocales.SPANISH);
    }

    private String bakerBusinessName(Quote quote) {
        return profiles.findByUserId(quote.getUserId()).map(UserProfile::getBakerCompleteName)
                .or(() -> users.find(quote.getUserId()).map(UserRow::displayName))
                .orElse("");
    }

    private static boolean hasEmail(String email) {
        return email != null && !email.isBlank();
    }
}
