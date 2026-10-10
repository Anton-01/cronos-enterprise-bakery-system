package com.ninsky.cronos.application.listener;

import com.ninsky.cronos.application.event.RecipeShared;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.model.recipe.RecipeShare;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareRepositoryPort;
import com.ninsky.cronos.eventing.application.EventEnvelope;
import com.ninsky.cronos.eventing.application.OutboxHandler;
import com.ninsky.cronos.iam.signin.AccountStanding;
import com.ninsky.cronos.iam.signin.AccountStandingCustomRepository;
import com.ninsky.cronos.kitchen.recipe.RecipeAggregate;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import com.ninsky.cronos.mail.application.MailRequest;
import com.ninsky.cronos.mail.application.Mailer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Emails the recipient of a recipe share link ({@code recipe.shared}). A link revoked or a recipe deleted before the
 * handler runs sends nothing.
 */
@Slf4j
@Component
public class RecipeShareMailHandler implements OutboxHandler<RecipeShared> {

    private final RecipeShareRepositoryPort shares;
    private final RecipeCustomRepository recipes;
    private final UserProfileRepositoryPort profiles;
    private final AccountStandingCustomRepository standings;
    private final Mailer mailer;
    private final String shareBaseUrl;

    public RecipeShareMailHandler(RecipeShareRepositoryPort shares, RecipeCustomRepository recipes, UserProfileRepositoryPort profiles,
                                  AccountStandingCustomRepository standings, Mailer mailer,
                                  @Value("${app.frontend.urlSharePublicRecipe}") String shareBaseUrl) {
        this.shares = shares;
        this.recipes = recipes;
        this.profiles = profiles;
        this.standings = standings;
        this.mailer = mailer;
        this.shareBaseUrl = shareBaseUrl;
    }

    @Override
    public Class<RecipeShared> eventClass() {
        return RecipeShared.class;
    }

    @Override
    public void handle(RecipeShared event, EventEnvelope envelope) {
        Optional<RecipeShare> share = shares.findById(event.shareId())
                .filter(s -> !s.isRevoked() && s.getRecipientEmail() != null && !s.getRecipientEmail().isBlank());
        Optional<RecipeAggregate> recipe = share.flatMap(s -> recipes.findLive(s.getRecipeId()));
        if (share.isEmpty() || recipe.isEmpty()) {
            log.info("Recipe share {} is gone or has no recipient; no email", event.shareId());
            return;
        }
        RecipeShare link = share.get();
        String senderName = profiles.findByUserId(link.getUserId()).map(UserProfile::getCompleteName).orElse("Cronos");
        String locale = standings.find(link.getUserId()).map(AccountStanding::locale).orElse(null);
        long days = ChronoUnit.DAYS.between(link.getCreatedAt().toLocalDate(), link.getExpiresAt().toLocalDate());
        mailer.send(MailRequest.of("recipe.shared", locale, link.getRecipientEmail(), Map.of(
                "senderName", senderName,
                "recipeName", recipe.get().head().name(),
                "shareUrl", shareBaseUrl + link.getShareToken(),
                "expirationDays", String.valueOf(days))));
    }
}
