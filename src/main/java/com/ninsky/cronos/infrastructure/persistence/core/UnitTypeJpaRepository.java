package com.ninsky.cronos.infrastructure.persistence.core;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.entity.enums.UnitDimension;
import com.ninsky.cronos.infrastructure.persistence.core.entity.UnitTypeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnitTypeJpaRepository extends JpaRepository<UnitTypeJpaEntity, Long>, JpaSpecificationExecutor<UnitTypeJpaEntity> {

    boolean existsByCodeIdentityIgnoreCase(String codeIdentity);

    boolean existsByCodeIdentityIgnoreCaseAndIdNot(String codeIdentity, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByDimension(UnitDimension dimension);

    boolean existsByDimensionAndIdNot(UnitDimension dimension, Long id);

    List<UnitTypeJpaEntity> findAllByStatusOrderByNameAsc(RecordStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UnitTypeJpaEntity e SET e.status = :status, e.updatedAt = CURRENT_TIMESTAMP, e.updatedBy = :actor WHERE e.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") RecordStatus status, @Param("actor") String actor);
}
