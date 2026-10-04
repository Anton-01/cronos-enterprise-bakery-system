package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.model.core.IngredientConversion;
import com.ninsky.cronos.domain.port.core.IngredientConversionRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.core.IngredientConversionJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.MeasurementUnitJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.IngredientConversionJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.IngredientConversionMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class IngredientConversionRepositoryAdapter implements IngredientConversionRepositoryPort {

    private final IngredientConversionJpaRepository jpaRepository;
    private final MeasurementUnitJpaRepository measurementUnitJpaRepository;
    private final IngredientConversionMapper mapper;

    public IngredientConversionRepositoryAdapter(IngredientConversionJpaRepository jpaRepository,
                                                  MeasurementUnitJpaRepository measurementUnitJpaRepository,
                                                  IngredientConversionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.measurementUnitJpaRepository = measurementUnitJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public IngredientConversion save(IngredientConversion conversion) {
        MeasurementUnitJpaEntity volumeUnit = measurementUnitJpaRepository.getReferenceById(conversion.getVolumeUnitId());
        MeasurementUnitJpaEntity massUnit = measurementUnitJpaRepository.getReferenceById(conversion.getMassUnitId());
        IngredientConversionJpaEntity saved = jpaRepository.save(mapper.toEntity(conversion, volumeUnit, massUnit));
        return mapper.toDomain(saved);
    }

    @Override
    public void delete(IngredientConversion conversion) {
        jpaRepository.findById(conversion.getId()).ifPresent(jpaRepository::delete);
    }

    @Override
    public List<IngredientConversion> findAllByIngredientId(UUID ingredientId) {
        return jpaRepository.findAllByIngredientIdOrderByIdAsc(ingredientId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<IngredientConversion> findByIngredientIdAndVolumeUnitIdAndUserId(UUID ingredientId, Long volumeUnitId, Long userId) {
        return jpaRepository.findByIngredientIdAndVolumeUnitIdAndUserId(ingredientId, volumeUnitId, userId).map(mapper::toDomain);
    }

    @Override
    public List<IngredientConversion> findAllByIngredientIdAndUserId(UUID materialId, Long userId) {
        return jpaRepository.findAllByIngredientIdAndUserId(materialId, userId).stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
