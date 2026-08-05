package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.infrastructure.persistence.recipe.entity.RecipeJpaEntity;
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
public interface RecipeJpaRepository extends JpaRepository<RecipeJpaEntity, UUID> {
    Optional<RecipeJpaEntity> findByIdAndUserId(UUID recipeId, UUID id);

    Page<RecipeJpaEntity> findByUserIdAndNameContainingIgnoreCase(UUID id, String search, Pageable pageable);

    Page<RecipeJpaEntity> findByUserId(UUID id, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RecipeJpaEntity r SET r.needsRecalculation = true, r.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE r.id IN (SELECT ri.recipe.id FROM RecipeIngredientJpaEntity ri WHERE ri.rawMaterialId = :rawMaterialId)")
    void markRecipesAsNeedingRecalculationByRawMaterialId(@Param("rawMaterialId") UUID rawMaterialId);

    boolean existsByIdAndUserId(UUID recipeId, UUID id);

    List<RecipeJpaEntity> findTop15ByUserIdAndNameContainingIgnoreCase(UUID userId, String name);

    List<RecipeJpaEntity> findTop15ByUserIdOrderByUpdatedAtDesc(UUID userId);
}
