package com.ninsky.cronos.application.service.recipe;

import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.request.recipe.CreateRecipeShareRequest;
import com.ninsky.cronos.application.response.recipe.*;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.RawMaterial;
import com.ninsky.cronos.domain.model.recipe.Recipe;
import com.ninsky.cronos.domain.model.recipe.RecipeIngredient;
import com.ninsky.cronos.domain.model.recipe.RecipeShare;
import com.ninsky.cronos.domain.model.recipe.RecipeShareAccessLog;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.RawMaterialRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeFileRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareAccessLogRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeShareRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeShareService {

    private final RecipeShareRepositoryPort shareRepository;
    private final RecipeShareAccessLogRepositoryPort accessLogRepository;
    private final RecipeRepositoryPort recipeRepository;
    private final RecipeFileRepositoryPort recipeFileRepository;
    private final UserRepositoryPort userRepository;
    private final UserProfileRepositoryPort userProfileRepository;
    private final MailService mailService;
    private final RawMaterialRepositoryPort rawMaterialRepository;
    private final MeasurementUnitRepositoryPort unitRepository;

    private final StoragePort cloudStorageService;

    @Value("${app.frontend.urlSharePublicRecipe}")
    private String frontendUrlSharePublicRecipe;

    @Transactional(readOnly = true)
    public List<RecipeShareResponse> getSharesByRecipeId(String username, UUID recipeId) {
        log.info("Retrieving the history of shared links for the recipe {} for the user {}", recipeId, username);

        User user = userRepository.findByUsername(username).orElseThrow();

        if (!recipeRepository.existsByIdAndUserId(recipeId, user.getId())) {
            throw new ResourceNotFoundException("Receta no encontrada o sin acceso");
        }

        return shareRepository.findByRecipeIdOrderByCreatedAtDesc(recipeId).stream()
                .map(this::mapToResponse).toList();
    }

    @Transactional
    public RecipeShareResponse generateShareLink(String username, UUID recipeId, CreateRecipeShareRequest request) {
        User user = userRepository.findByUsername(username).orElseThrow();
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada"));

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(request.expirationDays());

        RecipeShare share = RecipeShare.builder().recipeId(recipe.getId()).userId(user.getId()).shareToken(token)
                .recipientEmail(request.recipientEmail()).expiresAt(expiresAt).isRevoked(false).viewsCount(0).build();

        share = shareRepository.save(share);
        String shareUrl = frontendUrlSharePublicRecipe + token;

        UserProfile senderProfile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));
        sendEmailToSharedRecipe(request, recipe, senderProfile.getCompleteName(), shareUrl);

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

        Recipe recipe = recipeRepository.findById(share.getRecipeId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada"));
        UserProfile ownerProfile = userProfileRepository.findByUserId(recipe.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));

        // Collect all unique IDs so that only two database queries are made
        Set<UUID> materialIds = recipe.getIngredients().stream()
                .map(RecipeIngredient::getRawMaterialId).collect(java.util.stream.Collectors.toSet());

        Set<Long> unitIds = recipe.getIngredients().stream()
                .map(RecipeIngredient::getUnitId).collect(java.util.stream.Collectors.toSet());

        // Import catalogs and convert them into maps for instant search (O(1))
        Map<UUID, RawMaterial> materialsMap = rawMaterialRepository.findAllById(materialIds).stream()
                .collect(java.util.stream.Collectors.toMap(RawMaterial::getId, m -> m));

        Map<Long, MeasurementUnit> unitsMap = unitRepository.findAllById(unitIds).stream()
                .collect(java.util.stream.Collectors.toMap(MeasurementUnit::getId, u -> u));

        // Map to the DTO using Maps
        List<PublicIngredientDto> ingredients = recipe.getIngredients().stream().map(ing -> {
            RawMaterial material = materialsMap.get(ing.getRawMaterialId());
            MeasurementUnit unit = unitsMap.get(ing.getUnitId());

            return PublicIngredientDto.builder().name(material != null ? material.getName() : "Insumo Desconocido")
                    .quantity(ing.getQuantity()).unitName(unit != null ? unit.getName() : "")
                    .isOptional(ing.isOptional()).build();
        }).toList();

        List<PublicFileDto> files = recipeFileRepository.findByRecipeIdOrderByCreatedAtDesc(recipe.getId()).stream().map(file -> PublicFileDto.builder()
                .url(cloudStorageService.generateSignedUrl(file.getFilePath(), 120))
                .fileType(file.getFileType()).description(file.getDescription())
                .build()).toList();

        return PublicSharedRecipeResponse.builder().recipeName(recipe.getName())
                .description(recipe.getDescription()).yieldQuantity(recipe.getYieldQuantity())
                .yieldUnit(recipe.getYieldUnit()).preparationTimeMinutes(recipe.getPreparationTimeMinutes())
                .bakingTimeMinutes(recipe.getBakingTimeMinutes()).instructions(recipe.getInstructions())
                .storageInstructions(recipe.getStorageInstructions())
                .owner(PublicOwnerDto.builder().fullName(ownerProfile.getCompleteName())
                        .brandName("Cronos Professional Bakery").build())
                .expiresAt(share.getExpiresAt()).ingredients(ingredients).files(files).build();
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

    private void sendEmailToSharedRecipe(CreateRecipeShareRequest request, Recipe recipe, String username, String url) {
        if (request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
            EmailRequest emailRequest = EmailRequest.builder().to(request.recipientEmail())
                    .subject(username + " ha compartido una receta contigo")
                    .templateName("recipes/share-recipe")
                    .variables(Map.of("senderName", username,
                            "recipeName", recipe.getName(), "shareUrl", url,
                            "expirationDays", String.valueOf(request.expirationDays())
                    )).build();
            mailService.sendHtmlEmail(emailRequest);
            log.info("Email sent to {}", request.recipientEmail());
        }
    }

    private RecipeShareResponse mapToResponse(RecipeShare share) {
        return RecipeShareResponse.builder().id(share.getId()).shareUrl(frontendUrlSharePublicRecipe + share.getShareToken())
                .expiresAt(share.getExpiresAt()).viewsCount(share.getViewsCount())
                .isRevoked(share.isRevoked()).createdAt(share.getCreatedAt()).build();
    }
}
