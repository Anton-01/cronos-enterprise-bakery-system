package com.ninsky.cronos.domain.port.recipe;

import com.ninsky.cronos.domain.model.recipe.RecipeShare;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeShareRepositoryPort {
    RecipeShare save(RecipeShare share);
    Optional<RecipeShare> findById(UUID id);
    Optional<RecipeShare> findByShareToken(String shareToken);
    /** Links one user generated for a recipe (SYSTEM library recipes are shared by many tenants). */
    List<RecipeShare> findByRecipeIdAndUserIdOrderByCreatedAtDesc(UUID recipeId, UUID userId);
}
