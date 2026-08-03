package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.core.IngredientConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientConversionRepository extends JpaRepository<IngredientConversion, Long> {
    Optional<IngredientConversion> findByIngredientId(UUID ingredientId);
    Optional<IngredientConversion> findByIngredientIdAndVolumeUnitIdAndUserId(UUID ingredientId, Long volumeUnitId, Long userId);
    List<IngredientConversion> findAllByIngredientIdAndUserId(UUID materialId, Long userId);
}
