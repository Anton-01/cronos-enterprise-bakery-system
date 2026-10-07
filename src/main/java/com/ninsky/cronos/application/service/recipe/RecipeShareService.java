package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.event.RecipeSharedEvent;
import com.ninsky.cronos.application.request.recipe.CreateRecipeShareRequest;
import com.ninsky.cronos.application.response.recipe.*;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.model.recipe.RecipeShare;
import com.ninsky.cronos.domain.model.recipe.RecipeShareAccessLog;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareAccessLogRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.kitchen.ingredient.IngredientQueries;
import com.ninsky.cronos.kitchen.recipe.RecipeAggregate;
import com.ninsky.cronos.kitchen.recipe.RecipeStore;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileService;
import com.ninsky.cronos.kitchen.recipe.file.RecipeFileStore;
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
    private final RecipeStore recipeStore;
    private final RecipeFileStore recipeFileStore;
    private final RecipeFileService recipeFileService;
    private final UserRepositoryPort userRepository;
    private final UserProfileRepositoryPort userProfileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IngredientQueries ingredientQueries;
    private final UnitCatalog unitCatalog;

    @Value("${app.frontend.urlSharePublicRecipe}")
    private String frontendUrlSharePublicRecipe;

    @Transactional(readOnly = true)
    public List<RecipeShareResponse> getSharesByRecipeId(String username, UUID recipeId) {
        log.info("Retrieving the history of shared links for the recipe {} for the user {}", recipeId, username);

        User user = userRepository.findByUsername(username).orElseThrow();

        owned(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o sin acceso"));

        return shareRepository.findByRecipeIdOrderByCreatedAtDesc(recipeId).stream()
                .map(this::mapToResponse).toList();
    }

    @Transactional
    public RecipeShareResponse generateShareLink(String username, UUID recipeId, CreateRecipeShareRequest request) {
        User user = userRepository.findByUsername(username).orElseThrow();
        RecipeAggregate recipe = owned(recipeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada"));

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(request.expirationDays());

        RecipeShare share = RecipeShare.builder().recipeId(recipe.id()).userId(user.getId()).shareToken(token)
                .recipientEmail(request.recipientEmail()).expiresAt(expiresAt).isRevoked(false).viewsCount(0).build();

        share = shareRepository.save(share);
        String shareUrl = frontendUrlSharePublicRecipe + token;

        if (request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
            eventPublisher.publishEvent(RecipeSharedEvent.builder().shareId(share.getId()).build());
        }

        return RecipeShareResponse.builder().id(share.getId()).shareUrl(shareUrl)
                .expiresAt(share.getExpiresAt()).viewsCount(share.getViewsCount())
                .isRevoked(share.isRevoked()).createdAt(share.getCreatedAt()).build();
    }

    @Transactional
    public void revokeShareLink(String username, UUID shareId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        RecipeShare share = shareRepository.findById(shareId).orElseThrow(() -> new ResourceNotFoundException("Enlace no encontrado"));

        if (!share.getUserId().equals(user.getId())) {
            throw new ResourceNotFoundException("No tienes permisos para revocar este enlace");
        }

        share.setRevoked(true);
        shareRepository.save(share);
        log.info("Ephemeral link {} successfully revoked by {}", shareId, username);
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
        Map<UUID, IngredientQueries.Row> ingredients = ingredientQueries.findAll(viewer, language,
                recipe.lines().stream().map(RecipeAggregate.Line::ingredientId).collect(Collectors.toSet()));
        Map<Long, UnitInfo> units = unitCatalog.findAll(recipe.lines().stream().map(RecipeAggregate.Line::unitId).collect(Collectors.toSet()));

        List<PublicIngredientDto> lines = recipe.lines().stream().map(line -> PublicIngredientDto.builder()
                .name(Optional.ofNullable(ingredients.get(line.ingredientId())).map(IngredientQueries.Row::name).orElse("Insumo desconocido"))
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

    // Analytics for the owner of the shared recipe
    @Transactional(readOnly = true)
    public List<RecipeShareAccessLogResponse> getShareAnalytics(String username, UUID shareId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        RecipeShare share = shareRepository.findById(shareId).orElseThrow();

        if (!share.getUserId().equals(user.getId())) {
            throw new ResourceNotFoundException("Sin permisos");
        }

        return accessLogRepository.findByRecipeShareIdOrderByAccessedAtDesc(shareId).stream()
                .map(log -> RecipeShareAccessLogResponse.builder().id(log.getId()).accessedAt(log.getAccessedAt())
                        .ipAddress(log.getIpAddress()).userAgent(log.getUserAgent()).build())
                .collect(Collectors.toList());
    }


    private Optional<RecipeAggregate> owned(UUID recipeId, UUID userId) {
        return recipeStore.findVisible(recipeId, userId).filter(r -> userId.equals(r.head().ownerId()));
    }

    private RecipeShareResponse mapToResponse(RecipeShare share) {
        return RecipeShareResponse.builder().id(share.getId()).shareUrl(frontendUrlSharePublicRecipe + share.getShareToken())
                .expiresAt(share.getExpiresAt()).viewsCount(share.getViewsCount())
                .isRevoked(share.isRevoked()).createdAt(share.getCreatedAt()).build();
    }
}
