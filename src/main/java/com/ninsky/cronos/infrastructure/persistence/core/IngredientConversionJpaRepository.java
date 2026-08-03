package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.infrastructure.persistence.core.entity.IngredientConversionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientConversionJpaRepository extends JpaRepository<IngredientConversionJpaEntity, Long> {
    Optional<IngredientConversionJpaEntity> findByIngredientId(UUID ingredientId);
    Optional<IngredientConversionJpaEntity> findByIngredientIdAndVolumeUnitIdAndUserId(UUID ingredientId, Long volumeUnitId, Long userId);
    List<IngredientConversionJpaEntity> findAllByIngredientIdAndUserId(UUID materialId, Long userId);
}
