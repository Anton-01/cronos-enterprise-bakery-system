package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.domain.entity.recipes.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    Optional<Recipe> findByIdAndUserId(UUID recipeId, UUID id);

    Page<Recipe> findByUserIdAndNameContainingIgnoreCase(UUID id, String search, Pageable pageable);

    Page<Recipe> findByUserId(UUID id, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Recipe r SET r.needsRecalculation = true, r.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE r.id IN (SELECT ri.recipe.id FROM RecipeIngredient ri WHERE ri.rawMaterialId = :rawMaterialId)")
    void markRecipesAsNeedingRecalculationByRawMaterialId(@Param("rawMaterialId") UUID rawMaterialId);

    boolean existsByIdAndUserId(UUID recipeId, UUID id);

    // Buscar por nombre (para cuando el usuario escribe)
    List<Recipe> findTop15ByUserIdAndNameContainingIgnoreCase(UUID userId, String name);

    // Buscar las más recientes (para cuando el usuario hace clic en el input sin escribir nada)
    List<Recipe> findTop15ByUserIdOrderByUpdatedAtDesc(UUID userId);
}
