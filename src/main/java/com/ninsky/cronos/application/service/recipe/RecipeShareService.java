package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.event.RecipeSharedEvent;
import com.ninsky.cronos.application.request.recipe.CreateRecipeShareRequest;
import com.ninsky.cronos.application.response.recipe.*;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.model.recipe.RecipeShare;
import com.ninsky.cronos.domain.model.recipe.RecipeShareAccessLog;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareAccessLogRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareRepositoryPort;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.kitchen.ingredient.IngredientQueryCustomRepository;
import com.ninsky.cronos.kitchen.recipe.RecipeAggregate;
import com.ninsky.cronos.kitchen.recipe.RecipeCustomRepository;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileService;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileCustomRepository;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.kitchen.unit.UnitCatalog;
import com.ninsky.cronos.kitchen.unit.UnitInfo;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeShareService {

    private final RecipeShareRepositoryPort shareRepository;
    private final RecipeShareAccessLogRepositoryPort accessLogRepository;
    private final RecipeCustomRepository recipeStore;
    private final RecipeFileCustomRepository recipeFileStore;
    private final RecipeFileService recipeFileService;
    private final ActorProvider actors;
    private final UserProfileRepositoryPort userProfileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IngredientQueryCustomRepository ingredientQueries;
    private final UnitCatalog unitCatalog;

    @Value("${app.frontend.urlSharePublicRecipe}")
    private String frontendUrlSharePublicRecipe;

    /**
     * The caller's links for a recipe they can see: their own recipes and the SYSTEM library (the public
     * view already shows the sharer for library recipes). Other tenants' links are never listed.
     */
    @Transactional(readOnly = true)
    public List<RecipeShareResponse> getSharesByRecipeId(UUID recipeId) {
        UUID userId = actors.require().id();
        visible(recipeId, userId);
        return shareRepository.findByRecipeIdAndUserIdOrderByCreatedAtDesc(recipeId, userId).stream()
                .map(this::mapToResponse).toList();
    }

    @Transactional
    public RecipeShareResponse generateShareLink(UUID recipeId, CreateRecipeShareRequest request) {
        UUID userId = actors.require().id();
        RecipeAggregate recipe = visible(recipeId, userId);

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(request.expirationDays());

        RecipeShare share = RecipeShare.builder().recipeId(recipe.id()).userId(userId).shareToken(token)
                .recipientEmail(request.recipientEmail()).expiresAt(expiresAt).isRevoked(false).viewsCount(0).build();

        share = shareRepository.save(share);

        if (request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
            eventPublisher.publishEvent(RecipeSharedEvent.builder().shareId(share.getId()).build());
        }
        return mapToResponse(share);
    }

    @Transactional
    public void revokeShareLink(UUID recipeId, UUID shareId) {
        RecipeShare share = ownShare(recipeId, shareId);
        share.setRevoked(true);
        shareRepository.save(share);
        log.info("Ephemeral link {} successfully revoked by {}", shareId, share.getUserId());
    }

    // Public method - captures IP and Agent
    @Transactional
    public PublicSharedRecipeResponse getSharedRecipe(String token, String ipAddress, String userAgent) {
        RecipeShare share = shareRepository.findByShareToken(token).orElseThrow(() -> new ResourceNotFoundException("Enlace inválido o inexistente."));

        if (share.isRevoked()) throw new BusinessException("Este enlace ha sido revocado.");
        if (share.getExpiresAt().isBefore(LocalDateTime.now())) throw new BusinessException("El enlace caducó.");


        share.setViewsCount(share.getViewsCount() + 1);
        shareRepository.save(share);
        RecipeShareAccessLog logEntry = RecipeShareAccessLog.builder().recipeShareId(share.getId()).ipAddress(ipAddress).userAgent(userAgent).build();
        accessLogRepository.save(logEntry);

        RecipeAggregate recipe = recipeStore.findLive(share.getRecipeId())
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada"));
        RecipeAggregate.Head head = recipe.head();
        // SYSTEM recipes have no owner profile: the sharer is shown instead
        UUID viewer = head.system() ? share.getUserId() : head.ownerId();
        UserProfile ownerProfile = userProfileRepository.findByUserId(viewer)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));

        // Two batched lookups: ingredient names (owner's view) and cached units
        String language = KitchenMessages.language();
        Map<UUID, IngredientQueryCustomRepository.Row> ingredients = ingredientQueries.findAll(viewer, language,
                recipe.lines().stream().map(RecipeAggregate.Line::ingredientId).collect(Collectors.toSet()));
        Map<Long, UnitInfo> units = unitCatalog.findAll(recipe.lines().stream().map(RecipeAggregate.Line::unitId).collect(Collectors.toSet()));

        List<PublicIngredientDto> lines = recipe.lines().stream().map(line -> PublicIngredientDto.builder()
                .name(Optional.ofNullable(ingredients.get(line.ingredientId())).map(IngredientQueryCustomRepository.Row::name).orElse("Insumo desconocido"))
                .quantity(line.quantity())
                .unitName(Optional.ofNullable(units.get(line.unitId())).map(UnitInfo::name).orElse(""))
                .isOptional(line.optional()).build()).toList();

        List<PublicFileDto> files = recipeFileStore.list(recipe.id()).stream().map(file -> PublicFileDto.builder()
                .url(recipeFileService.signed(file.storageKey(), 120))
                .fileType(file.mimeType()).description(file.description())
                .build()).toList();

        return PublicSharedRecipeResponse.builder().recipeName(head.name())
                .description(head.description()).yieldQuantity(head.yieldQuantity())
                .yieldUnit(head.yieldUnit()).preparationTimeMinutes(head.prepMinutes())
                .bakingTimeMinutes(head.bakeMinutes()).instructions(head.processHtml())
                .storageInstructions(head.storageInstructions())
                .owner(PublicOwnerDto.builder().fullName(ownerProfile.getCompleteName())
                        .brandName("Cronos Professional Bakery").build())
                .expiresAt(share.getExpiresAt()).ingredients(lines).files(files).build();
    }

    /** Access log of one of the caller's links. */
    @Transactional(readOnly = true)
    public List<RecipeShareAccessLogResponse> getShareAnalytics(UUID recipeId, UUID shareId) {
        RecipeShare share = ownShare(recipeId, shareId);
        return accessLogRepository.findByRecipeShareIdOrderByAccessedAtDesc(share.getId()).stream()
                .map(log -> RecipeShareAccessLogResponse.builder().id(log.getId()).accessedAt(log.getAccessedAt())
                        .ipAddress(log.getIpAddress()).userAgent(log.getUserAgent()).build())
                .toList();
    }

    /** A live recipe the user may share: own or SYSTEM library; anything else is 404 (K8). */
    private RecipeAggregate visible(UUID recipeId, UUID userId) {
        return recipeStore.findVisible(recipeId, userId).orElseThrow(() -> ApiException.notFound("kitchen.recipe.notFound"));
    }

    /** One of the caller's links of {@code recipeId}; a foreign or mismatched link is 404, never 403. */
    private RecipeShare ownShare(UUID recipeId, UUID shareId) {
        UUID userId = actors.require().id();
        return shareRepository.findById(shareId)
                .filter(share -> share.getUserId().equals(userId) && share.getRecipeId().equals(recipeId))
                .orElseThrow(() -> ApiException.notFound("kitchen.share.notFound"));
    }

    private RecipeShareResponse mapToResponse(RecipeShare share) {
        return RecipeShareResponse.builder().id(share.getId()).shareUrl(frontendUrlSharePublicRecipe + share.getShareToken())
                .expiresAt(share.getExpiresAt()).viewsCount(share.getViewsCount())
                .isRevoked(share.isRevoked()).createdAt(share.getCreatedAt()).build();
    }
}
