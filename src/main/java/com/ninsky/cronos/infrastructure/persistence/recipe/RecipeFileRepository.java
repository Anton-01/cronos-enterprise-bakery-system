package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.domain.entity.recipes.RecipeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeFileRepository extends JpaRepository<RecipeFile, UUID> {
    List<RecipeFile> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId);
    Optional<RecipeFile> findByIdAndRecipeId(UUID id, UUID recipeId);
}
