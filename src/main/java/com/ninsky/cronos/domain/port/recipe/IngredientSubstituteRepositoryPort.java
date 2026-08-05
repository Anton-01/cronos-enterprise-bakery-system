package com.ninsky.cronos.domain.port.recipe;

import com.ninsky.cronos.domain.model.recipe.IngredientSubstitute;

import java.util.Optional;
import java.util.UUID;

public interface IngredientSubstituteRepositoryPort {
    IngredientSubstitute save(IngredientSubstitute substitute);
    Optional<IngredientSubstitute> findByUserIdAndOriginalIngredientIdAndSubstituteMaterialId(UUID userId, UUID originalIngredientId, UUID substituteMaterialId);
}
