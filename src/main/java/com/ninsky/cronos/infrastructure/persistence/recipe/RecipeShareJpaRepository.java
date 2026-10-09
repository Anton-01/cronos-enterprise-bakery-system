package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeShareJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeShareJpaRepository extends JpaRepository<RecipeShareJpaEntity, UUID> {
    Optional<RecipeShareJpaEntity> findByShareToken(String shareToken);
    List<RecipeShareJpaEntity> findByRecipeIdAndUserIdOrderByCreatedAtDesc(UUID recipeId, UUID userId);
}
