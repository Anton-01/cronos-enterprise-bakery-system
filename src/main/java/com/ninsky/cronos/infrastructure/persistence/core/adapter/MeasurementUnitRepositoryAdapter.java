package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.infrastructure.persistence.core.MeasurementUnitJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.UnitTypeJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.core.mapper.MeasurementUnitMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class MeasurementUnitRepositoryAdapter implements MeasurementUnitRepositoryPort {

    private final MeasurementUnitJpaRepository jpaRepository;
    private final UnitTypeJpaRepository unitTypeJpaRepository;
    private final MeasurementUnitMapper mapper;

    public MeasurementUnitRepositoryAdapter(MeasurementUnitJpaRepository jpaRepository,
                                             UnitTypeJpaRepository unitTypeJpaRepository,
                                             MeasurementUnitMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.unitTypeJpaRepository = unitTypeJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public MeasurementUnit save(MeasurementUnit measurementUnit) {
        // unitTypeId is validated by the caller (UnitTypeRepositoryPort.findById) before save is
        // invoked, so a managed-reference lookup avoids a redundant SELECT here.
        UnitTypeJpaEntity unitType = unitTypeJpaRepository.getReferenceById(measurementUnit.getUnitTypeId());
        MeasurementUnitJpaEntity saved = jpaRepository.save(mapper.toEntity(measurementUnit, unitType));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<MeasurementUnit> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<MeasurementUnit> findAllById(Iterable<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<MeasurementUnit> findByName(String name) {
        return jpaRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return jpaRepository.existsByNameIgnoreCase(name);
    }

    @Override
    public Page<MeasurementUnit> findSystemMeasurementUnits(Pageable pageable) {
        return jpaRepository.findSystemMeasurementUnits(pageable).map(mapper::toDomain);
    }

    @Override
    public Optional<MeasurementUnit> findByUnitTypeIdAndIsBaseUnitTrue(Long unitTypeId) {
        return jpaRepository.findByUnitTypeIdAndIsBaseUnitTrue(unitTypeId).map(mapper::toDomain);
    }

    @Override
    public Optional<MeasurementUnit> findByCodeIdentity(String code) {
        return jpaRepository.findByCodeIdentity(code).map(mapper::toDomain);
    }

    @Override
    public int countByUnitTypeId(Long unitTypeId) {
        return jpaRepository.countByUnitTypeId(unitTypeId);
    }

    @Override
    public int updateStatus(Long id, RecordStatus status) {
        return jpaRepository.updateStatus(id, status);
    }
}
