package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.domain.entity.recipes.RecipeShareAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeShareAccessLogRepository extends JpaRepository<RecipeShareAccessLog, UUID> {
    List<RecipeShareAccessLog> findByRecipeShareIdOrderByAccessedAtDesc(UUID recipeShareId);
}
