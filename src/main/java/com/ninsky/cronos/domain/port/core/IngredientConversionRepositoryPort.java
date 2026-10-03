package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.model.core.IngredientConversion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngredientConversionRepositoryPort {

    IngredientConversion save(IngredientConversion conversion);

    void delete(IngredientConversion conversion);

    /** Every density rule of the ingredient (one per volume unit), oldest first. */
    List<IngredientConversion> findAllByIngredientId(UUID ingredientId);

    Optional<IngredientConversion> findByIngredientIdAndVolumeUnitIdAndUserId(UUID ingredientId, Long volumeUnitId, Long userId);

    List<IngredientConversion> findAllByIngredientIdAndUserId(UUID materialId, Long userId);
}
