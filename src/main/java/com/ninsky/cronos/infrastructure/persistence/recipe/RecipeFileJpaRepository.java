package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeFileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeFileJpaRepository extends JpaRepository<RecipeFileJpaEntity, UUID> {
    List<RecipeFileJpaEntity> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId);
    Optional<RecipeFileJpaEntity> findByIdAndRecipeId(UUID id, UUID recipeId);
}
