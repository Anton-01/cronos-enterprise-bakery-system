package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.infrastructure.persistence.recipe.entity.IngredientSubstituteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientSubstituteJpaRepository extends JpaRepository<IngredientSubstituteJpaEntity, UUID> {
    Optional<IngredientSubstituteJpaEntity> findByUserIdAndOriginalIngredientIdAndSubstituteMaterialId(UUID id, UUID rawMaterialId, UUID substituteMaterialId);
}
