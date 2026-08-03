package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.core.MeasurementUnit;
import com.ninsky.cronos.domain.entity.core.UnitType;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, Long> {
    Optional<MeasurementUnit> findByName(String name);
    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT m FROM MeasurementUnit m WHERE m.isSystemDefault = true")
    Page<MeasurementUnit> findSystemMeasurementUnits(Pageable pageable);

    Optional<MeasurementUnit> findByUnitTypeIdAndIsBaseUnitTrue(Long unitTypeId);

    Optional<MeasurementUnit> findByCodeIdentity(String code);
    int countByUnitType(UnitType unitType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MeasurementUnit m SET m.status = :status, m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") RecordStatus status);
}
