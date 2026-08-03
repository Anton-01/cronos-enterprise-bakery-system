package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.application.response.core.RawMaterialListResponse;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.RawMaterial;
import com.ninsky.cronos.domain.port.core.RawMaterialRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.core.AllergenJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.MeasurementUnitJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.RawMaterialJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.AllergenJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.RawMaterialJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.RawMaterialMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class RawMaterialRepositoryAdapter implements RawMaterialRepositoryPort {

    private final RawMaterialJpaRepository jpaRepository;
    private final MeasurementUnitJpaRepository measurementUnitJpaRepository;
    private final AllergenJpaRepository allergenJpaRepository;
    private final RawMaterialMapper mapper;

    public RawMaterialRepositoryAdapter(RawMaterialJpaRepository jpaRepository,
                                         MeasurementUnitJpaRepository measurementUnitJpaRepository,
                                         AllergenJpaRepository allergenJpaRepository,
                                         RawMaterialMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.measurementUnitJpaRepository = measurementUnitJpaRepository;
        this.allergenJpaRepository = allergenJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public RawMaterial save(RawMaterial rawMaterial) {
        MeasurementUnitJpaEntity purchaseUnit = measurementUnitJpaRepository.getReferenceById(rawMaterial.getPurchaseUnitId());
        Set<AllergenJpaEntity> allergens = rawMaterial.getAllergenIds().isEmpty()
                ? Set.of()
                : StreamSupport.stream(allergenJpaRepository.findAllById(rawMaterial.getAllergenIds()).spliterator(), false)
                        .collect(Collectors.toSet());
        RawMaterialJpaEntity saved = jpaRepository.save(mapper.toEntity(rawMaterial, purchaseUnit, allergens));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RawMaterial> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<RawMaterial> findAllById(Iterable<UUID> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Page<RawMaterialListResponse> findAllForListByUserId(UUID userId, Pageable pageable) {
        return jpaRepository.findAllForListByUserId(userId, pageable);
    }

    @Override
    public int updateStatus(UUID id, RecordStatus status) {
        return jpaRepository.updateStatus(id, status);
    }
}
