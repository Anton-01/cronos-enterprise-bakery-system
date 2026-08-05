package com.ninsky.cronos.application.service.recipe;


import com.ninsky.cronos.application.request.recipe.CreateRecipeRequest;
import com.ninsky.cronos.application.request.recipe.UpdateRecipeRequest;
import com.ninsky.cronos.application.response.recipe.*;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.core.Allergen;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.RawMaterial;
import com.ninsky.cronos.domain.model.recipe.Recipe;
import com.ninsky.cronos.domain.model.recipe.RecipeFile;
import com.ninsky.cronos.domain.model.recipe.RecipeFixedCost;
import com.ninsky.cronos.domain.model.recipe.RecipeIngredient;
import com.ninsky.cronos.domain.port.core.AllergenRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.RawMaterialRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeFileRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.recipe.RecipeRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {
    private final RecipeRepositoryPort recipeRepository;
    private final RecipeFileRepositoryPort recipeFileRepository;
    private final UserRepositoryPort userRepository;
    private final RawMaterialRepositoryPort rawMaterialRepository;
    private final MeasurementUnitRepositoryPort unitRepository;
    private final AllergenRepositoryPort allergenRepository;
    private final StoragePort cloudStorageService;

    @Transactional(readOnly = true)
    public RecipeDetailResponse getRecipeById(String username, UUID recipeId) {
        log.info("Retrieving rich details from the recipe {} for: {}", recipeId, username);

        User user = userRepository.findByUsername(username).orElseThrow();
        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o sin acceso"));

        Set<UUID> materialIds = recipe.getIngredients().stream().map(RecipeIngredient::getRawMaterialId).collect(Collectors.toSet());
        Set<Long> unitIds = recipe.getIngredients().stream().map(RecipeIngredient::getUnitId).collect(Collectors.toSet());

        // Access the database once via the catalog and quickly build a dictionary (map) in memory
        Map<UUID, RawMaterial> materialsMap = rawMaterialRepository.findAllById(materialIds).stream().collect(Collectors.toMap(RawMaterial::getId, m -> m));
        Map<Long, MeasurementUnit> unitsMap = unitRepository.findAllById(unitIds).stream().collect(Collectors.toMap(MeasurementUnit::getId, u -> u));

        Set<UUID> allergenIds = materialsMap.values().stream()
                .flatMap(m -> m.getAllergenIds().stream())
                .collect(Collectors.toSet());
        Map<UUID, String> allergenNamesById = allergenRepository.findAllById(allergenIds).stream()
                .collect(Collectors.toMap(Allergen::getId, Allergen::getName));

        List<RecipeIngredientDto> ingredientsDto = recipe.getIngredients().stream().map(ing -> {

            RawMaterial material = materialsMap.get(ing.getRawMaterialId());
            MeasurementUnit unit = unitsMap.get(ing.getUnitId());

            boolean hasAllergen = false;
            List<String> allergenNames = new ArrayList<>();

            if (material != null && material.getAllergenIds() != null && !material.getAllergenIds().isEmpty()) {
                hasAllergen = true;
                allergenNames = material.getAllergenIds().stream()
                        .map(allergenNamesById::get)
                        .filter(Objects::nonNull)
                        .toList();
            }

            return RecipeIngredientDto.builder().id(ing.getId()).rawMaterialName(material != null ? material.getName() : "Desconocido")
                    .hasAllergen(hasAllergen).allergenNames(allergenNames).unitName(unit != null ? unit.getName() : "N/A")
                    .quantity(ing.getQuantity()).displayOrder(ing.getDisplayOrder()).isOptional(ing.isOptional()).notes(ing.getNotes())
                    .costPerUnit(ing.getCostPerUnit()).totalCost(ing.getTotalCost()).build();
        }).toList();
        List<RecipeFixedCostDto> fixedCostsDto = recipe.getFixedCosts().stream().filter(RecipeFixedCost::isActive).map(fc -> RecipeFixedCostDto.builder().id(fc.getId())
                        .userFixedCostName(fc.getName()).description(fc.getDescription())
                        .type(fc.getType()).defaultAmount(fc.getRate()).calculationMethod(fc.getCalculationMethod())
                        .timeInMinutes(fc.getTimeInMinutes()).percentage(fc.getPercentage())
                        .calculatedCost(fc.getCalculatedAmount() != null ? fc.getCalculatedAmount() : BigDecimal.ZERO)
                        .isActive(fc.isActive()).build())
                .toList();

        return RecipeDetailResponse.builder().id(recipe.getId()).name(recipe.getName())
                .description(recipe.getDescription()).categoryId(recipe.getCategoryId())
                .yieldQuantity(recipe.getYieldQuantity()).yieldUnit(recipe.getYieldUnit())
                .preparationTimeMinutes(recipe.getPreparationTimeMinutes())
                .bakingTimeMinutes(recipe.getBakingTimeMinutes())
                .coolingTimeMinutes(recipe.getCoolingTimeMinutes())
                .instructions(recipe.getInstructions())
                .storageInstructions(recipe.getStorageInstructions())
                .shelfLifeDays(recipe.getShelfLifeDays())
                .status(recipe.getStatus()).isActive(recipe.isActive())
                .needsRecalculation(recipe.isNeedsRecalculation())
                .currentVersion(recipe.getCurrentVersion())
                .createdAt(recipe.getCreatedAt()).updatedAt(recipe.getUpdatedAt())
                .ingredients(ingredientsDto).fixedCosts(fixedCostsDto)
                .build();
    }

    @Transactional
    public RecipeResponse createRecipe(String username, CreateRecipeRequest request) {
        log.info("Creating a new recipe for the user: {}", username);

        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Recipe recipe = Recipe.builder().name(request.name()).description(request.description())
                .userId(user.getId()).categoryId(request.categoryId())
                .yieldQuantity(request.yieldQuantity())
                .yieldUnit(request.yieldUnit()).preparationTimeMinutes(request.preparationTimeMinutes())
                .bakingTimeMinutes(request.bakingTimeMinutes())
                .coolingTimeMinutes(request.coolingTimeMinutes())
                .instructions(request.instructions()).storageInstructions(request.storageInstructions())
                .shelfLifeDays(request.shelfLifeDays()).status("DRAFT")
                .isActive(true).needsRecalculation(true).currentVersion(1).build();

        recipe = recipeRepository.save(recipe);
        return mapToResponse(recipe);
    }

    @Transactional(readOnly = true)
    public Page<RecipeResponse> getMyRecipes(String username, Pageable pageable, String search) {
        log.info("Retrieving page-by-page results for: {}", username);
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Page<Recipe> recipes;
        if (search != null && !search.trim().isEmpty()) {
            recipes = recipeRepository.findByUserIdAndNameContainingIgnoreCase(user.getId(), search, pageable);
        } else {
            recipes = recipeRepository.findByUserId(user.getId(), pageable);
        }

        return recipes.map(this::mapToResponse);
    }

    @Transactional
    public RecipeResponse updateRecipe(String username, UUID recipeId, UpdateRecipeRequest request) {
        log.info("Updating recipe {} for user: {}", recipeId, username);
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada o no tienes permisos para editarla"));

        boolean yieldChanged = mapFieldsToUpdate(request, recipe);

        if (yieldChanged) {
            log.info("A change in performance has been detected in recipe {}. Marking for recalculation.", recipeId);
            recipe.setNeedsRecalculation(true);
        }

        recipe = recipeRepository.save(recipe);
        return mapToResponse(recipe);
    }

    @Transactional(readOnly = true)
    public List<SimpleRecipeResponse> searchSimpleRecipes(String username, String searchTerm) {
        log.info("User {} is searching for simple recipes. Search term: '{}'", username, searchTerm);
        User user = userRepository.findByUsername(username).orElseThrow();

        List<Recipe> recipes;
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            recipes = recipeRepository.findTop15ByUserIdOrderByUpdatedAtDesc(user.getId());
        } else {
            recipes = recipeRepository.findTop15ByUserIdAndNameContainingIgnoreCase(user.getId(), searchTerm.trim());
        }

        log.debug("Found {} simple recipes matching the criteria for user {}", recipes.size(), user.getId());

        return recipes.stream().map(recipe -> {
            String imageUrl = recipeFileRepository.findByRecipeIdOrderByCreatedAtDesc(recipe.getId()).stream()
                    .filter(RecipeFile::isPrimary).findFirst()
                    .map(file -> cloudStorageService.generateSignedUrl(file.getFilePath(), 60))
                    .orElse(null);

            return SimpleRecipeResponse.builder().id(recipe.getId()).name(recipe.getName())
                    .description(recipe.getDescription()).primaryImageUrl(imageUrl)
                    .totalCost(recipe.getTotalCost()).build();
        }).toList();
    }

    private boolean mapFieldsToUpdate(UpdateRecipeRequest request, Recipe recipe) {
        boolean requiresRecalculation = false;

        if (request.name() != null) recipe.setName(request.name());
        if (request.description() != null) recipe.setDescription(request.description());

        // Check if the QUANTITY of output has changed
        if (request.yieldQuantity() != null) {
            if (recipe.getYieldQuantity() == null || recipe.getYieldQuantity().compareTo(request.yieldQuantity()) != 0) {
                requiresRecalculation = true;
            }
            recipe.setYieldQuantity(request.yieldQuantity());
        }

        // Check whether the unit of measurement has changed (e.g., from “cakes” to “slices”)
        if (request.yieldUnit() != null) {
            if (!request.yieldUnit().equals(recipe.getYieldUnit())) {
                requiresRecalculation = true;
            }
            recipe.setYieldUnit(request.yieldUnit());
        }

        if (request.status() != null) recipe.setStatus(request.status());
        if (request.categoryId() != null) recipe.setCategoryId(request.categoryId());
        if (request.preparationTimeMinutes() != null) recipe.setPreparationTimeMinutes(request.preparationTimeMinutes());
        if (request.bakingTimeMinutes() != null) recipe.setBakingTimeMinutes(request.bakingTimeMinutes());
        if (request.coolingTimeMinutes() != null) recipe.setCoolingTimeMinutes(request.coolingTimeMinutes());
        if (request.instructions() != null) recipe.setInstructions(request.instructions());
        if (request.storageInstructions() != null) recipe.setStorageInstructions(request.storageInstructions());
        if (request.shelfLifeDays() != null) recipe.setShelfLifeDays(request.shelfLifeDays());

        return requiresRecalculation;
    }

    private RecipeResponse mapToResponse(Recipe recipe) {
        return RecipeResponse.builder().id(recipe.getId()).name(recipe.getName())
                .description(recipe.getDescription()).yieldQuantity(recipe.getYieldQuantity())
                .yieldUnit(recipe.getYieldUnit()).status(recipe.getStatus())
                .isActive(recipe.isActive()).needsRecalculation(recipe.isNeedsRecalculation())
                .currentVersion(recipe.getCurrentVersion()).createdAt(recipe.getCreatedAt())
                .updatedAt(recipe.getUpdatedAt()).build();
    }
}
