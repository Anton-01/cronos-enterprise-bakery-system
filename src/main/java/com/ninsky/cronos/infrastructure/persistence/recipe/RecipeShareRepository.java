package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.domain.entity.recipes.RecipeShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeShareRepository extends JpaRepository<RecipeShare, UUID> {
    Optional<RecipeShare> findByShareToken(String shareToken);
    List<RecipeShare> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId);
}
