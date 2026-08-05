package com.ninsky.cronos.domain.port.recipe;

import com.ninsky.cronos.domain.model.recipe.RecipeFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeFileRepositoryPort {
    RecipeFile save(RecipeFile file);
    List<RecipeFile> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId);
    Optional<RecipeFile> findByIdAndRecipeId(UUID id, UUID recipeId);
    void delete(RecipeFile file);

    /** Forces pending changes to the DB before a subsequent read in the same transaction needs to see them. */
    void flush();
}
