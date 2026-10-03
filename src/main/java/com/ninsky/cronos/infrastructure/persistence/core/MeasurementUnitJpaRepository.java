package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.infrastructure.persistence.core.entity.MeasurementUnitJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeasurementUnitJpaRepository extends JpaRepository<MeasurementUnitJpaEntity, Long>, JpaSpecificationExecutor<MeasurementUnitJpaEntity> {

    /** Redeclared only to join-fetch the unit type: list pages render its code/name/dimension per row. */
    @Override
    @EntityGraph(attributePaths = "unitType")
    Page<MeasurementUnitJpaEntity> findAll(Specification<MeasurementUnitJpaEntity> spec, Pageable pageable);

    @EntityGraph(attributePaths = "unitType")
    Optional<MeasurementUnitJpaEntity> findWithUnitTypeById(Long id);

    @EntityGraph(attributePaths = "unitType")
    @Query("""
            SELECT m FROM MeasurementUnitJpaEntity m
            WHERE m.status = :status AND m.unitType.status = :status
            ORDER BY m.unitType.name ASC, m.multiplierToBase ASC, m.name ASC
            """)
    List<MeasurementUnitJpaEntity> findAllSelectable(@Param("status") RecordStatus status);

    Optional<MeasurementUnitJpaEntity> findByCodeIdentity(String code);

    Optional<MeasurementUnitJpaEntity> findByUnitTypeIdAndIsBaseUnitTrue(Long unitTypeId);

    boolean existsByCodeIdentity(String codeIdentity);

    boolean existsByCodeIdentityAndIdNot(String codeIdentity, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByUnitTypeId(Long unitTypeId);

    long countByUnitTypeIdAndStatus(Long unitTypeId, RecordStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MeasurementUnitJpaEntity m SET m.status = :status, m.updatedAt = CURRENT_TIMESTAMP, m.updatedBy = :actor WHERE m.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") RecordStatus status, @Param("actor") String actor);
}
