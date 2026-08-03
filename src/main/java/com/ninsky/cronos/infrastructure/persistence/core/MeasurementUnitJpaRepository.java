package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MeasurementUnitJpaRepository extends JpaRepository<MeasurementUnitJpaEntity, Long> {
    Optional<MeasurementUnitJpaEntity> findByName(String name);
    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT m FROM MeasurementUnitJpaEntity m WHERE m.isSystemDefault = true")
    Page<MeasurementUnitJpaEntity> findSystemMeasurementUnits(Pageable pageable);

    Optional<MeasurementUnitJpaEntity> findByUnitTypeIdAndIsBaseUnitTrue(Long unitTypeId);

    Optional<MeasurementUnitJpaEntity> findByCodeIdentity(String code);

    int countByUnitTypeId(Long unitTypeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MeasurementUnitJpaEntity m SET m.status = :status, m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") RecordStatus status);
}
