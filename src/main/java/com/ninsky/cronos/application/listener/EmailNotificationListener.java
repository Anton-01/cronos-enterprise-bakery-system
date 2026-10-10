package com.ninsky.cronos.application.listener;

import com.ninsky.cronos.application.event.QuoteEmailRequestedEvent;
import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.quote.QuoteRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Business-notification emails triggered by completed actions (quote sent, password reset
 * requested, recipe shared) — kept separate from {@code SecurityEventListener}, which stays
 * scoped to security-specific events. Every handler is {@code @TransactionalEventListener(AFTER_COMMIT)}:
 * it only fires once the publishing method's transaction has actually committed, so an email is
 * never sent for a change that then rolled back. Each event carries only ids/primitives (no JPA or
 * domain object references), so handlers re-fetch whatever they need via the already-hexagonalized
 * ports — safe by construction here since domain models are plain POJOs with no lazy-loading concerns.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final MailService mailService;
    private final QuoteRepositoryPort quoteRepository;
    private final UserProfileRepositoryPort userProfileRepository;

    @Value("${app.frontend.urlSharePublicQuote}")
    private String urlSharePublicQuote;


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleQuoteEmailRequested(QuoteEmailRequestedEvent event) {
        Quote quote = quoteRepository.findById(event.quoteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        UserProfile userProfile = userProfileRepository.findByUserId(quote.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));

        EmailRequest request = EmailRequest.builder().to(quote.getClientEmail()).subject(userProfile.getQuoteCompleteName())
                .templateName("quote/share-quote").variables(Map.of("completeName", userProfile.getCompleteName(),
                        "clientName", quote.getClientName(), "quoteNumber", quote.getQuoteNumber(),
                        "publicQuoteUrl", urlSharePublicQuote + quote.getPublicToken(),
                        "validUntil", quote.getValidUntil().toLocalDate().toString()))
                .build();

        log.info("Dispatching quote email for quote {}", quote.getId());
        mailService.sendHtmlEmail(request);
    }
}
