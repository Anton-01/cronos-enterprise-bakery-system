package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.domain.entity.recipes.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, UUID> {
    Optional<RecipeIngredient> findByIdAndRecipeId(UUID recipeIngredientId, UUID recipeId);
}
