package com.ninsky.cronos.infrastructure.persistence.recipe.mapper;

import com.ninsky.cronos.domain.model.recipe.Recipe;
import com.ninsky.cronos.domain.model.recipe.RecipeFixedCost;
import com.ninsky.cronos.domain.model.recipe.RecipeIngredient;
import com.ninsky.cronos.domain.model.recipe.RecipeSubRecipeItem;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeFixedCostJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeIngredientJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeSubRecipeJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RecipeMapper {

    public Recipe toDomain(RecipeJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Recipe.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .categoryId(entity.getCategoryId())
                .yieldQuantity(entity.getYieldQuantity())
                .yieldUnit(entity.getYieldUnit())
                .preparationTimeMinutes(entity.getPreparationTimeMinutes())
                .bakingTimeMinutes(entity.getBakingTimeMinutes())
                .coolingTimeMinutes(entity.getCoolingTimeMinutes())
                .status(entity.getStatus())
                .isActive(entity.isActive())
                .needsRecalculation(entity.isNeedsRecalculation())
                .currentVersion(entity.getCurrentVersion())
                .instructions(entity.getInstructions())
                .storageInstructions(entity.getStorageInstructions())
                .shelfLifeDays(entity.getShelfLifeDays())
                .userId(entity.getUserId())
                .totalCost(entity.getTotalCost())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .ingredients(entity.getIngredients().stream().map(this::toDomain).collect(Collectors.toList()))
                .fixedCosts(entity.getFixedCosts().stream().map(this::toDomain).collect(Collectors.toList()))
                .subRecipes(entity.getSubRecipes().stream().map(this::toDomain).collect(Collectors.toList()))
                .build();
    }

    public RecipeJpaEntity toEntity(Recipe domain) {
        if (domain == null) {
            return null;
        }
        RecipeJpaEntity entity = RecipeJpaEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .categoryId(domain.getCategoryId())
                .yieldQuantity(domain.getYieldQuantity())
                .yieldUnit(domain.getYieldUnit())
                .preparationTimeMinutes(domain.getPreparationTimeMinutes())
                .bakingTimeMinutes(domain.getBakingTimeMinutes())
                .coolingTimeMinutes(domain.getCoolingTimeMinutes())
                .status(domain.getStatus())
                .isActive(domain.isActive())
                .needsRecalculation(domain.isNeedsRecalculation())
                .currentVersion(domain.getCurrentVersion())
                .instructions(domain.getInstructions())
                .storageInstructions(domain.getStorageInstructions())
                .shelfLifeDays(domain.getShelfLifeDays())
                .userId(domain.getUserId())
                .totalCost(domain.getTotalCost())
                .version(domain.getVersion())
                .build();
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        List<RecipeIngredientJpaEntity> ingredientEntities = domain.getIngredients().stream()
                .map(i -> toEntity(i, entity)).collect(Collectors.toList());
        List<RecipeFixedCostJpaEntity> fixedCostEntities = domain.getFixedCosts().stream()
                .map(fc -> toEntity(fc, entity)).collect(Collectors.toList());
        List<RecipeSubRecipeJpaEntity> subRecipeEntities = domain.getSubRecipes().stream()
                .map(sr -> toEntity(sr, entity)).collect(Collectors.toList());

        entity.setIngredients(ingredientEntities);
        entity.setFixedCosts(fixedCostEntities);
        entity.setSubRecipes(subRecipeEntities);
        return entity;
    }

    private RecipeIngredient toDomain(RecipeIngredientJpaEntity entity) {
        return RecipeIngredient.builder()
                .id(entity.getId())
                .rawMaterialId(entity.getRawMaterialId())
                .quantity(entity.getQuantity())
                .unitId(entity.getUnitId())
                .displayOrder(entity.getDisplayOrder())
                .isOptional(entity.isOptional())
                .notes(entity.getNotes())
                .costPerUnit(entity.getCostPerUnit())
                .totalCost(entity.getTotalCost())
                .version(entity.getVersion())
                .build();
    }

    private RecipeIngredientJpaEntity toEntity(RecipeIngredient domain, RecipeJpaEntity parent) {
        return RecipeIngredientJpaEntity.builder()
                .id(domain.getId())
                .recipe(parent)
                .rawMaterialId(domain.getRawMaterialId())
                .quantity(domain.getQuantity())
                .unitId(domain.getUnitId())
                .displayOrder(domain.getDisplayOrder())
                .isOptional(domain.isOptional())
                .notes(domain.getNotes())
                .costPerUnit(domain.getCostPerUnit())
                .totalCost(domain.getTotalCost())
                .version(domain.getVersion())
                .build();
    }

    private RecipeFixedCost toDomain(RecipeFixedCostJpaEntity entity) {
        return RecipeFixedCost.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .rate(entity.getRate())
                .calculationMethod(entity.getCalculationMethod())
                .timeInMinutes(entity.getTimeInMinutes())
                .percentage(entity.getPercentage())
                .isActive(entity.isActive())
                .masterFixedCostId(entity.getMasterFixedCostId())
                .calculatedAmount(entity.getCalculatedAmount())
                .version(entity.getVersion())
                .build();
    }

    private RecipeFixedCostJpaEntity toEntity(RecipeFixedCost domain, RecipeJpaEntity parent) {
        return RecipeFixedCostJpaEntity.builder()
                .id(domain.getId())
                .recipe(parent)
                .name(domain.getName())
                .description(domain.getDescription())
                .type(domain.getType())
                .rate(domain.getRate())
                .calculationMethod(domain.getCalculationMethod())
                .timeInMinutes(domain.getTimeInMinutes())
                .percentage(domain.getPercentage())
                .isActive(domain.isActive())
                .masterFixedCostId(domain.getMasterFixedCostId())
                .calculatedAmount(domain.getCalculatedAmount())
                .version(domain.getVersion())
                .build();
    }

    private RecipeSubRecipeItem toDomain(RecipeSubRecipeJpaEntity entity) {
        return RecipeSubRecipeItem.builder()
                .id(entity.getId())
                .subRecipeId(entity.getSubRecipe().getId())
                .quantity(entity.getQuantity())
                .displayOrder(entity.getDisplayOrder())
                .notes(entity.getNotes())
                .totalCost(entity.getTotalCost())
                .version(entity.getVersion())
                .build();
    }

    /** {@code subRecipe} is set as a bare-id reference (no full load) — a JPA proxy resolves fine off just the id for FK purposes on save. */
    private RecipeSubRecipeJpaEntity toEntity(RecipeSubRecipeItem domain, RecipeJpaEntity parent) {
        RecipeJpaEntity subRecipeRef = new RecipeJpaEntity();
        subRecipeRef.setId(domain.getSubRecipeId());
        return RecipeSubRecipeJpaEntity.builder()
                .id(domain.getId())
                .parentRecipe(parent)
                .subRecipe(subRecipeRef)
                .quantity(domain.getQuantity())
                .displayOrder(domain.getDisplayOrder())
                .notes(domain.getNotes())
                .totalCost(domain.getTotalCost())
                .version(domain.getVersion())
                .build();
    }
}
