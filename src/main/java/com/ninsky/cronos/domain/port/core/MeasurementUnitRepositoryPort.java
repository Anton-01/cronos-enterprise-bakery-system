package com.ninsky.cronos.domain.port.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface MeasurementUnitRepositoryPort {

    MeasurementUnit save(MeasurementUnit measurementUnit);

    Optional<MeasurementUnit> findById(Long id);

    List<MeasurementUnit> findAllById(Iterable<Long> ids);

    Optional<MeasurementUnit> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    Page<MeasurementUnit> findSystemMeasurementUnits(Pageable pageable);

    Optional<MeasurementUnit> findByUnitTypeIdAndIsBaseUnitTrue(Long unitTypeId);

    Optional<MeasurementUnit> findByCodeIdentity(String code);

    int countByUnitTypeId(Long unitTypeId);

    int updateStatus(Long id, RecordStatus status);
}
