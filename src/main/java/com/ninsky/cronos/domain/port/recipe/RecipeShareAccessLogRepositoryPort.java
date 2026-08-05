package com.ninsky.cronos.domain.port.recipe;

import com.ninsky.cronos.domain.model.recipe.RecipeShareAccessLog;

import java.util.List;
import java.util.UUID;

public interface RecipeShareAccessLogRepositoryPort {
    RecipeShareAccessLog save(RecipeShareAccessLog log);
    List<RecipeShareAccessLog> findByRecipeShareIdOrderByAccessedAtDesc(UUID recipeShareId);
}
