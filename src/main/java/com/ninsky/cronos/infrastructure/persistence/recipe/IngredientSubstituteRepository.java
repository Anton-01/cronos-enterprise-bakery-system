package com.ninsky.cronos.infrastructure.persistence.recipe;

import com.ninsky.cronos.domain.entity.recipes.IngredientSubstitute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientSubstituteRepository extends JpaRepository<IngredientSubstitute, UUID> {
    Optional<IngredientSubstitute> findByUserIdAndOriginalIngredientIdAndSubstituteMaterialId(UUID id, UUID rawMaterialId, UUID substituteMaterialId);
}
