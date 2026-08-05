package com.ninsky.cronos.domain.port.recipe;

import com.ninsky.cronos.domain.model.recipe.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepositoryPort {
    Recipe save(Recipe recipe);
    Optional<Recipe> findById(UUID id);
    Optional<Recipe> findByIdAndUserId(UUID recipeId, UUID userId);
    Page<Recipe> findByUserIdAndNameContainingIgnoreCase(UUID userId, String search, Pageable pageable);
    Page<Recipe> findByUserId(UUID userId, Pageable pageable);
    boolean existsByIdAndUserId(UUID recipeId, UUID userId);
    List<Recipe> findTop15ByUserIdAndNameContainingIgnoreCase(UUID userId, String name);
    List<Recipe> findTop15ByUserIdOrderByUpdatedAtDesc(UUID userId);

    /** Flags every recipe using {@code rawMaterialId} as {@code needsRecalculation}. Mirrors the existing bulk {@code @Modifying} query. */
    void markRecipesAsNeedingRecalculationByRawMaterialId(UUID rawMaterialId);
}
