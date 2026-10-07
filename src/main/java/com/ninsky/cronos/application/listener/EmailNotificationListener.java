package com.ninsky.cronos.application.listener;

import com.ninsky.cronos.application.event.PasswordResetRequestedEvent;
import com.ninsky.cronos.application.event.QuoteEmailRequestedEvent;
import com.ninsky.cronos.application.event.RecipeSharedEvent;
import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.model.quote.Quote;
import com.ninsky.cronos.domain.model.recipe.RecipeShare;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.quote.QuoteRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareRepositoryPort;
import com.ninsky.cronos.kitchen.recipe.RecipeAggregate;
import com.ninsky.cronos.kitchen.recipe.RecipeStore;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final UserRepositoryPort userRepository;
    private final UserProfileRepositoryPort userProfileRepository;
    private final RecipeShareRepositoryPort recipeShareRepository;
    private final RecipeStore recipeStore;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.frontend.urlSharePublicQuote}")
    private String urlSharePublicQuote;

    @Value("${app.frontend.urlSharePublicRecipe}")
    private String urlSharePublicRecipe;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePasswordResetRequested(PasswordResetRequestedEvent event) {
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        String resetLink = frontendUrl + "/auth/reset-password?token=" + event.resetToken();

        EmailRequest request = EmailRequest.builder().to(user.getEmail())
                .subject(event.requestedByAdmin() ? "Restablecimiento de Contraseña" : "Recuperación de Contraseña - Cronos Bakery")
                .templateName("auth/password-reset")
                .variables(Map.of("username", user.getUsername(), "resetLink", resetLink))
                .build();

        log.info("Dispatching password reset email for user {} (requestedByAdmin={})", user.getId(), event.requestedByAdmin());
        mailService.sendHtmlEmail(request);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRecipeShared(RecipeSharedEvent event) {
        RecipeShare share = recipeShareRepository.findById(event.shareId())
                .orElseThrow(() -> new ResourceNotFoundException("Enlace no encontrado"));
        RecipeAggregate recipe = recipeStore.findLive(share.getRecipeId())
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada"));
        UserProfile senderProfile = userProfileRepository.findByUserId(share.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));

        String senderName = senderProfile.getCompleteName();
        String shareUrl = urlSharePublicRecipe + share.getShareToken();
        long expirationDays = ChronoUnit.DAYS.between(share.getCreatedAt().toLocalDate(), share.getExpiresAt().toLocalDate());

        EmailRequest request = EmailRequest.builder().to(share.getRecipientEmail())
                .subject(senderName + " ha compartido una receta contigo")
                .templateName("recipes/share-recipe")
                .variables(Map.of("senderName", senderName,
                        "recipeName", recipe.head().name(), "shareUrl", shareUrl,
                        "expirationDays", String.valueOf(expirationDays)
                )).build();

        log.info("Dispatching recipe share email for share {}", share.getId());
        mailService.sendHtmlEmail(request);
    }
}
