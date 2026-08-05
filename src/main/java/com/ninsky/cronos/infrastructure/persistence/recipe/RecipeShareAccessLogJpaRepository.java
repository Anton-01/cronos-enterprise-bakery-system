package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeShareAccessLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeShareAccessLogJpaRepository extends JpaRepository<RecipeShareAccessLogJpaEntity, UUID> {
    List<RecipeShareAccessLogJpaEntity> findByRecipeShareIdOrderByAccessedAtDesc(UUID recipeShareId);
}
